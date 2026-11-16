package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
class ParameterSettingInitializer {

    private final ReactiveExtensionClient client;

    private final ParameterSettingSecretCipher secretCipher;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    Mono<Void> onApplicationEvent(ExtensionInitializedEvent ignored) {
        return migrateConfigMap(
                        ParameterSetting.LEGACY_PARAMETER_SETTINGS_DEFAULT,
                        ParameterSetting.PARAMETER_SETTINGS_DEFAULT)
                .then(migrateConfigMap(
                        ParameterSetting.LEGACY_PARAMETER_SETTINGS, ParameterSetting.PARAMETER_SETTINGS))
                .then(ensureConfigMap(ParameterSetting.PARAMETER_SETTINGS));
    }

    private Mono<Void> migrateConfigMap(String legacyName, String targetName) {
        return client.fetch(ConfigMap.class, targetName)
                .hasElement()
                .flatMap(targetExists -> {
                    if (Boolean.TRUE.equals(targetExists)) {
                        return Mono.empty();
                    }
                    return client.fetch(ConfigMap.class, legacyName)
                            .flatMap(legacy -> {
                                log.info(
                                        "Migrating legacy parameter settings ConfigMap [{}] to [{}]",
                                        legacyName,
                                        targetName);
                                return client.create(migratedConfigMap(legacy, targetName, legacyName));
                            })
                            .then();
                });
    }

    private ConfigMap migratedConfigMap(ConfigMap legacy, String targetName, String legacyName) {
        var migrated = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(targetName);
        metadata.setLabels(copyOf(legacy.getMetadata().getLabels()));
        metadata.setAnnotations(copyOf(legacy.getMetadata().getAnnotations()));
        metadata.getAnnotations().put(ParameterSetting.MIGRATION_ANNOTATION, legacyName);
        migrated.setMetadata(metadata);
        var data = copyOf(legacy.getData());
        synthesizeNewGroups(data);
        migrated.setData(data);
        return migrated;
    }

    private void synthesizeNewGroups(LinkedHashMap<String, String> data) {
        var basic = readObject(data.get(ParameterSetting.Basic.GROUP));
        if (!data.containsKey(ParameterSetting.Site.GROUP) && basic != null) {
            var site = JsonMapper.shared().createObjectNode();
            copyText(site, "siteName", basic, "title");
            copyText(site, "siteDescription", basic, "subtitle");
            copyText(site, "siteUrl", basic, "externalUrl");
            copyText(site, "logoDark", basic, "logo");
            copyText(site, "logoLight", basic, "logo");
            copyText(site, "favicon", basic, "favicon");
            data.put(ParameterSetting.Site.GROUP, site.toString());
        }
        if (!data.containsKey(ParameterSetting.Session.GROUP)) {
            var session = JsonMapper.shared().createObjectNode();
            session.put("defaultUserGroup", "normal-users");
            data.put(ParameterSetting.Session.GROUP, session.toString());
        }
        var captcha = readObject(data.get(ParameterSetting.Captcha.GROUP));
        if (captcha != null) {
            copyValue(captcha, "loginCaptchaEnabled", "enabled");
            copyValue(captcha, "captchaProvider", "provider");
            data.put(ParameterSetting.Captcha.GROUP, captcha.toString());
        }
        var mail = readObject(data.get(ParameterSetting.Mail.GROUP));
        if (mail != null) {
            copyText(mail, "mailFromName", basic, "title");
            copyText(mail, "mailFromAddress", mail, "from");
            copyText(mail, "smtpHost", mail, "host");
            copyValue(mail, "smtpPort", "port");
            copyValue(mail, "smtpForceSsl", "ssl");
            encryptText(mail, "username");
            encryptText(mail, "password");
            copyText(mail, "smtpUsername", mail, "username");
            copyText(mail, "smtpPassword", mail, "password");
            data.put(ParameterSetting.Mail.GROUP, mail.toString());
        }
    }

    private void encryptText(ObjectNode node, String field) {
        var value = node.get(field);
        if (value != null
                && value.isString()
                && !value.asString().isEmpty()
                && !secretCipher.isEncrypted(value.asString())) {
            node.put(field, secretCipher.encrypt(value.asString()));
        }
    }

    private static void copyValue(ObjectNode target, String targetField, String sourceField) {
        var value = target.get(sourceField);
        if (value != null) {
            target.set(targetField, value);
        }
    }

    private static void copyText(ObjectNode target, String targetField, ObjectNode source, String sourceField) {
        if (target == null || source == null) {
            return;
        }
        var value = source.get(sourceField);
        if (value != null && value.isString() && !value.asString().isEmpty()) {
            target.put(targetField, value.asString());
        }
    }

    private static ObjectNode readObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            var node = JsonMapper.shared().readTree(json);
            return node.isObject() ? (ObjectNode) node : null;
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed legacy parameter setting group JSON");
            return null;
        }
    }

    private Mono<Void> ensureConfigMap(String name) {
        return client.fetch(ConfigMap.class, name)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Initializing parameter settings ConfigMap [{}]...", name);
                    var configMap = new ConfigMap();
                    configMap.setMetadata(new Metadata());
                    configMap.getMetadata().setName(name);
                    configMap.setData(new HashMap<>());
                    return client.create(configMap);
                }))
                .then();
    }

    private static LinkedHashMap<String, String> copyOf(Map<String, String> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }
}
