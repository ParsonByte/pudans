package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.exception.RequestBodyValidationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterSettingGroupService {

    public static final Set<String> NEW_GROUPS =
            Set.of("site", "session", "captcha", "mail", "audit", "server");

    public static final Set<String> LEGACY_GROUPS = Set.of("basic", "user", "seo", "authProvider");

    private static final Map<String, Class<?>> GROUP_TYPES = Map.of(
            "site", ParameterSetting.Site.class,
            "session", ParameterSetting.Session.class,
            "captcha", ParameterSetting.Captcha.class,
            "mail", ParameterSetting.Mail.class,
            "audit", ParameterSetting.Audit.class,
            "server", ParameterSetting.Server.class);

    private static final Duration RETRY_BACKOFF = Duration.ofMillis(100);

    private final ParameterSettingFetcher fetcher;

    private final ReactiveExtensionClient client;

    private final ApplicationEventPublisher eventPublisher;

    private final Validator validator;

    private final ParameterSettingSecretCipher secretCipher;

    public static boolean isKnownGroup(String group) {
        return NEW_GROUPS.contains(group) || LEGACY_GROUPS.contains(group);
    }

    public Mono<ObjectNode> getGroup(String group) {
        if (!isKnownGroup(group)) {
            return Mono.error(new ServerWebInputException("未知的参数设置组：" + group));
        }
        return fetcher.fetch(group, ObjectNode.class)
                .switchIfEmpty(Mono.fromSupplier(JsonMapper.shared()::createObjectNode))
                .map(node -> {
                    if (ParameterSetting.Site.GROUP.equals(group)) {
                        node.put("announcementVersion", announcementVersion(text(node, "siteAnnouncement")));
                    }
                    ParameterSettingSensitiveFields.mask(node, group);
                    return node;
                });
    }

    public Mono<ObjectNode> getLegacyGroup(String group) {
        return fetcher.fetch(group, ObjectNode.class)
                .switchIfEmpty(Mono.fromSupplier(JsonMapper.shared()::createObjectNode))
                .map(node -> {
                    if (ParameterSetting.Site.GROUP.equals(group)) {
                        node.put("announcementVersion", announcementVersion(text(node, "siteAnnouncement")));
                    }
                    ParameterSettingSensitiveFields.mask(node, group);
                    return node;
                });
    }

    public Mono<UpdateResult> updateLegacyGroup(String group, ObjectNode incoming) {
        if (incoming == null) {
            return Mono.error(new ServerWebInputException("请求体不能为空。"));
        }
        return Mono.defer(() -> fetcher.getConfigMap()
                        .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                                "缺少参数设置 ConfigMap。")))
                        .flatMap(configMap -> {
                            if (configMap.getData() == null) {
                                configMap.setData(new HashMap<>());
                            }
                            var previousData = new LinkedHashMap<>(configMap.getData());
                            var existing = readObject(configMap.getData().get(group));
                            var restartRequired = restartRequired(group, incoming, existing);
                            var persisted = merge(existing, incoming);
                            ParameterSettingSensitiveFields.restoreMasked(persisted, existing, group);
                            ParameterSettingSensitiveFields.encrypt(persisted, group, secretCipher);
                            mirrorLegacyToNew(group, persisted, configMap.getData());
                            configMap.getData().put(group, persisted.toString());
                            return client.update(configMap)
                                    .doOnNext(updated -> eventPublisher.publishEvent(
                                            new ParameterSettingChangedEvent(this, previousData, updated.getData())))
                                    .thenReturn(new UpdateResult(restartRequired));
                        }))
                .retryWhen(Retry.backoff(5, RETRY_BACKOFF)
                        .filter(OptimisticLockingFailureException.class::isInstance));
    }

    public Mono<UpdateResult> updateGroup(String group, ObjectNode incoming) {
        if (!isKnownGroup(group)) {
            return Mono.error(new ServerWebInputException("未知的参数设置组：" + group));
        }
        if (incoming == null) {
            return Mono.error(new ServerWebInputException("请求体不能为空。"));
        }
        return Mono.defer(() -> fetcher.getConfigMap()
                        .switchIfEmpty(Mono.error(() -> new IllegalStateException(
                                "缺少参数设置 ConfigMap。")))
                        .flatMap(configMap -> updateConfigMap(group, incoming, configMap)))
                .retryWhen(Retry.backoff(5, RETRY_BACKOFF)
                        .filter(OptimisticLockingFailureException.class::isInstance));
    }

    private Mono<UpdateResult> updateConfigMap(String group, ObjectNode incoming, ConfigMap configMap) {
        if (configMap.getData() == null) {
            configMap.setData(new HashMap<>());
        }
        var previousData = new LinkedHashMap<>(configMap.getData());
        var existing = readObject(configMap.getData().get(group));
        var persisted = merge(existing, incoming);
        var restartRequired = restartRequired(group, incoming, existing);
        return fetcher.fetch(group, ObjectNode.class)
                .switchIfEmpty(Mono.fromSupplier(JsonMapper.shared()::createObjectNode))
                .map(defaults -> merge(defaults, persisted))
                .map(effective -> {
                    applyComputedValues(group, persisted, effective, existing, incoming);
                    ParameterSettingSensitiveFields.restoreMasked(persisted, existing, group);
                    ParameterSettingSensitiveFields.encrypt(persisted, group, secretCipher);
                    mirrorLegacyFields(group, persisted);
                    if (ParameterSetting.Site.GROUP.equals(group)) {
                        mirrorSiteToBasic(persisted, configMap.getData());
                    }
                    ParameterSettingSensitiveFields.decryptForValidation(effective, group, secretCipher);
                    validate(group, effective);
                    configMap.getData().put(group, persisted.toString());
                    return restartRequired;
                })
                .flatMap(restart -> client.update(configMap)
                        .doOnNext(updated -> eventPublisher.publishEvent(
                                new ParameterSettingChangedEvent(this, previousData, updated.getData())))
                        .thenReturn(new UpdateResult(restart)));
    }

    private void applyComputedValues(
            String group,
            ObjectNode persisted,
            ObjectNode effective,
            ObjectNode existing,
            ObjectNode incoming) {
        if (ParameterSetting.Site.GROUP.equals(group)) {
            var version = announcementVersion(text(persisted, "siteAnnouncement"));
            persisted.put("announcementVersion", version);
            effective.put("announcementVersion", version);
        }
        if (ParameterSetting.Server.GROUP.equals(group)) {
            applySiteId(persisted, effective, existing, incoming);
        }
    }

    private void applySiteId(ObjectNode persisted, ObjectNode effective, ObjectNode existing, ObjectNode incoming) {
        var existingSiteId = text(existing, "siteId");
        var requestedSiteId = text(incoming, "siteId");
        if (StringUtils.hasText(existingSiteId)
                && StringUtils.hasText(requestedSiteId)
                && !existingSiteId.equals(requestedSiteId)) {
            var errors = new BeanPropertyBindingResult(new ParameterSetting.Server(), ParameterSetting.Server.GROUP);
            errors.rejectValue("siteId", "readOnly", "siteId 为只读。");
            throw new RequestBodyValidationException(errors);
        }
        var siteId = StringUtils.hasText(existingSiteId) ? existingSiteId : requestedSiteId;
        if (!StringUtils.hasText(siteId)) {
            siteId = UUID.randomUUID().toString();
        }
        persisted.put("siteId", siteId);
        effective.put("siteId", siteId);
    }

    private void validate(String group, ObjectNode effective) {
        var type = GROUP_TYPES.get(group);
        if (type == null) {
            return;
        }
        Object model;
        try {
            model = JsonMapper.shared().convertValue(effective, type);
        } catch (RuntimeException e) {
            throw new ServerWebInputException("无法读取 " + group + " 设置。", null, e);
        }
        var errors = new BeanPropertyBindingResult(model, group);
        validator.validate(model, errors);
        if ("server".equals(group) && model instanceof ParameterSetting.Server server) {
            if (StringUtils.hasText(server.getGcCron()) && !CronExpression.isValidExpression(server.getGcCron())) {
                errors.rejectValue("gcCron", "invalid", "gcCron 必须是有效的六段式 cron 表达式。");
            }
        }
        if ("audit".equals(group) && model instanceof ParameterSetting.Audit audit) {
            validateAuditRules(audit.getAuditRecordRules(), errors);
        }
        if (errors.hasErrors()) {
            throw new RequestBodyValidationException(errors);
        }
    }

    private static void validateAuditRules(
            ParameterSetting.AuditRecordRules rules, BeanPropertyBindingResult errors) {
        if (rules == null) {
            return;
        }
        var mode = rules.getMode();
        if (StringUtils.hasText(mode)
                && !"WHITELIST".equalsIgnoreCase(mode)
                && !"BLACKLIST".equalsIgnoreCase(mode)) {
            errors.rejectValue("auditRecordRules.mode", "invalid", "mode 必须为 WHITELIST 或 BLACKLIST。");
        }
        validateRuleList(rules.getOperations(), "auditRecordRules.operations", errors);
        validateRuleList(rules.getResourceTypes(), "auditRecordRules.resourceTypes", errors);
    }

    private static void validateRuleList(List<String> values, String field, BeanPropertyBindingResult errors) {
        if (values == null) {
            return;
        }
        if (values.size() > 100) {
            errors.rejectValue(field, "size", field + " 最多只能包含 100 个条目。");
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 128)) {
            errors.rejectValue(field, "invalid", field + " 的条目长度必须为 1-128 个字符。");
        }
    }

    private boolean restartRequired(String group, ObjectNode incoming, ObjectNode existing) {
        if (!ParameterSetting.Server.GROUP.equals(group)) {
            return false;
        }
        return sensitiveValueChanged(incoming, existing, "masterKey")
                || sensitiveValueChanged(incoming, existing, "hashIdSalt");
    }

    private boolean sensitiveValueChanged(ObjectNode incoming, ObjectNode existing, String field) {
        var requested = incoming.get(field);
        if (requested == null || !requested.isString()) {
            return false;
        }
        var value = requested.asString();
        if (ParameterSettingSensitiveFields.MASK.equals(value)) {
            return false;
        }
        var stored = existing == null ? null : existing.get(field);
        var storedValue = stored == null || !stored.isString() ? null : secretCipher.decrypt(stored.asString());
        if (value.isEmpty()) {
            return StringUtils.hasText(storedValue);
        }
        return !Objects.equals(value, storedValue);
    }

    private static void mirrorLegacyFields(String group, ObjectNode node) {
        if (ParameterSetting.Captcha.GROUP.equals(group)) {
            mirror(node, "loginCaptchaEnabled", "enabled");
            mirror(node, "captchaProvider", "provider");
        }
        if (ParameterSetting.Mail.GROUP.equals(group)) {
            mirror(node, "smtpHost", "host");
            mirror(node, "smtpPort", "port");
            mirror(node, "smtpUsername", "username");
            mirror(node, "smtpPassword", "password");
            mirror(node, "mailFromAddress", "from");
            mirror(node, "smtpForceSsl", "ssl");
        }
    }

    private static void mirror(ObjectNode node, String source, String target) {
        var value = node.get(source);
        if (value != null) {
            node.set(target, value);
        }
    }

    private static void mirrorIfAbsent(ObjectNode node, String source, String target) {
        if (!node.has(target)) {
            mirror(node, source, target);
        }
    }

    private static void mirrorSiteToBasic(ObjectNode site, Map<String, String> data) {
        var basic = readObject(data.get(ParameterSetting.Basic.GROUP));
        copyText(site, "siteName", basic, "title");
        copyText(site, "siteDescription", basic, "subtitle");
        copyText(site, "siteUrl", basic, "externalUrl");
        copyText(site, "logoDark", basic, "logo");
        copyText(site, "favicon", basic, "favicon");
        data.put(ParameterSetting.Basic.GROUP, basic.toString());
    }

    private static void mirrorLegacyToNew(String group, ObjectNode node, Map<String, String> data) {
        if (ParameterSetting.Captcha.GROUP.equals(group)) {
            mirrorIfAbsent(node, "enabled", "loginCaptchaEnabled");
            mirrorIfAbsent(node, "provider", "captchaProvider");
        }
        if (ParameterSetting.Mail.GROUP.equals(group)) {
            mirrorIfAbsent(node, "host", "smtpHost");
            mirrorIfAbsent(node, "port", "smtpPort");
            mirrorIfAbsent(node, "username", "smtpUsername");
            mirrorIfAbsent(node, "password", "smtpPassword");
            mirrorIfAbsent(node, "from", "mailFromAddress");
            mirrorIfAbsent(node, "ssl", "smtpForceSsl");
        }
        if (ParameterSetting.Basic.GROUP.equals(group)) {
            var site = readObject(data.get(ParameterSetting.Site.GROUP));
            copyText(node, "title", site, "siteName");
            copyText(node, "subtitle", site, "siteDescription");
            copyText(node, "externalUrl", site, "siteUrl");
            copyText(node, "logo", site, "logoDark");
            copyText(node, "logo", site, "logoLight");
            copyText(node, "favicon", site, "favicon");
            data.put(ParameterSetting.Site.GROUP, site.toString());
        }
    }

    private static void copyText(ObjectNode source, String sourceField, ObjectNode target, String targetField) {
        var value = source.get(sourceField);
        if (value != null && value.isString() && !value.asString().isEmpty()) {
            target.put(targetField, value.asString());
        }
    }

    public Mono<ParameterSetting.Site> getSite() {
        return fetcher.fetch(ParameterSetting.Site.GROUP, ParameterSetting.Site.class)
                .defaultIfEmpty(new ParameterSetting.Site())
                .flatMap(site -> fetcher.fetch(ParameterSetting.Basic.GROUP, ParameterSetting.Basic.class)
                        .defaultIfEmpty(new ParameterSetting.Basic())
                        .map(basic -> fillSiteFallback(site, basic)));
    }

    private static ParameterSetting.Site fillSiteFallback(ParameterSetting.Site site, ParameterSetting.Basic basic) {
        if (!StringUtils.hasText(site.getSiteName()) && StringUtils.hasText(basic.getTitle())) {
            site.setSiteName(basic.getTitle());
        }
        if (!StringUtils.hasText(site.getSiteDescription()) && StringUtils.hasText(basic.getSubtitle())) {
            site.setSiteDescription(basic.getSubtitle());
        }
        if (!StringUtils.hasText(site.getSiteUrl()) && StringUtils.hasText(basic.getExternalUrl())) {
            site.setSiteUrl(basic.getExternalUrl());
        }
        if (!StringUtils.hasText(site.getLogoDark()) && StringUtils.hasText(basic.getLogo())) {
            site.setLogoDark(basic.getLogo());
        }
        if (!StringUtils.hasText(site.getLogoLight()) && StringUtils.hasText(basic.getLogo())) {
            site.setLogoLight(basic.getLogo());
        }
        if (!StringUtils.hasText(site.getFavicon()) && StringUtils.hasText(basic.getFavicon())) {
            site.setFavicon(basic.getFavicon());
        }
        site.setAnnouncementVersion(announcementVersion(site.getSiteAnnouncement()));
        return site;
    }

    public Mono<ParameterSetting.Session> getSession() {
        return fetcher.fetch(ParameterSetting.Session.GROUP, ParameterSetting.Session.class)
                .defaultIfEmpty(new ParameterSetting.Session());
    }

    public Mono<ParameterSetting.Captcha> getCaptcha() {
        return fetcher.fetch(ParameterSetting.Captcha.GROUP, ParameterSetting.Captcha.class)
                .defaultIfEmpty(new ParameterSetting.Captcha())
                .flatMap(captcha -> fetcher.fetch(ParameterSetting.Captcha.GROUP, ObjectNode.class)
                        .defaultIfEmpty(JsonMapper.shared().createObjectNode())
                        .map(node -> fillCaptchaFallback(captcha, node)));
    }

    private static ParameterSetting.Captcha fillCaptchaFallback(
            ParameterSetting.Captcha captcha, ObjectNode node) {
        if (captcha.getLoginCaptchaEnabled() == null && node.has("enabled")) {
            captcha.setLoginCaptchaEnabled(node.get("enabled").asBoolean(false));
        }
        if (!StringUtils.hasText(captcha.getCaptchaProvider()) && node.has("provider")) {
            captcha.setCaptchaProvider(node.get("provider").asString());
        }
        return captcha;
    }

    public Mono<ParameterSetting.Mail> getMail() {
        return fetcher.fetch(ParameterSetting.Mail.GROUP, ParameterSetting.Mail.class)
                .defaultIfEmpty(new ParameterSetting.Mail())
                .flatMap(mail -> fetcher.fetch(ParameterSetting.Mail.GROUP, ObjectNode.class)
                        .defaultIfEmpty(JsonMapper.shared().createObjectNode())
                        .map(node -> fillMailFallback(mail, node)));
    }

    private ParameterSetting.Mail fillMailFallback(ParameterSetting.Mail mail, ObjectNode node) {
        if (!StringUtils.hasText(mail.getSmtpHost())) {
            mail.setSmtpHost(text(node, "host"));
        }
        if (mail.getSmtpPort() == null && node.has("port")) {
            mail.setSmtpPort(node.get("port").isNumber() ? node.get("port").asInt() : null);
        }
        if (!StringUtils.hasText(mail.getSmtpUsername())) {
            mail.setSmtpUsername(decrypt(text(node, "username")));
        }
        if (!StringUtils.hasText(mail.getSmtpPassword())) {
            mail.setSmtpPassword(decrypt(text(node, "password")));
        }
        if (!StringUtils.hasText(mail.getMailFromAddress())) {
            mail.setMailFromAddress(text(node, "from"));
        }
        if (mail.getSmtpForceSsl() == null && node.has("ssl")) {
            mail.setSmtpForceSsl(node.get("ssl").asBoolean(false));
        }
        mail.setSmtpUsername(decrypt(mail.getSmtpUsername()));
        mail.setSmtpPassword(decrypt(mail.getSmtpPassword()));
        return mail;
    }

    public Mono<ParameterSetting.Audit> getAudit() {
        return fetcher.fetch(ParameterSetting.Audit.GROUP, ParameterSetting.Audit.class)
                .defaultIfEmpty(new ParameterSetting.Audit());
    }

    public Mono<ParameterSetting.Server> getServer() {
        return fetcher.fetch(ParameterSetting.Server.GROUP, ParameterSetting.Server.class)
                .defaultIfEmpty(new ParameterSetting.Server())
                .map(server -> {
                    server.setMasterKey(decrypt(server.getMasterKey()));
                    server.setHashIdSalt(decrypt(server.getHashIdSalt()));
                    return server;
                });
    }

    private String decrypt(String value) {
        return StringUtils.hasText(value) ? secretCipher.decrypt(value) : value;
    }

    private static ObjectNode readObject(String json) {
        if (!StringUtils.hasText(json)) {
            return JsonMapper.shared().createObjectNode();
        }
        try {
            JsonNode node = JsonMapper.shared().readTree(json);
            return node.isObject() ? (ObjectNode) node : JsonMapper.shared().createObjectNode();
        } catch (RuntimeException e) {
            log.warn("忽略格式错误的参数设置组 JSON");
            return JsonMapper.shared().createObjectNode();
        }
    }

    private static ObjectNode merge(ObjectNode base, ObjectNode overlay) {
        var result = base.deepCopy();
        overlay.properties().forEach(entry -> {
            var key = entry.getKey();
            var value = entry.getValue();
            var existing = result.get(key);
            if (existing != null && existing.isObject() && value.isObject()) {
                result.set(key, merge((ObjectNode) existing, (ObjectNode) value));
            } else {
                result.set(key, value.deepCopy());
            }
        });
        return result;
    }

    private static String text(ObjectNode node, String field) {
        if (node == null) {
            return null;
        }
        var value = node.get(field);
        return value != null && value.isString() ? value.asString() : null;
    }

    private static String announcementVersion(String announcement) {
        if (!StringUtils.hasText(announcement)) {
            return "";
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(announcement.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record UpdateResult(boolean restartRequired) {}
}
