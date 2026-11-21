package cn.tolim.pudans.extension;

import static cn.tolim.pudans.extension.contract.Unstructured.OBJECT_MAPPER;
import static cn.tolim.pudans.extension.ExtensionStoreUtil.buildStoreName;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.Scheme;
import cn.tolim.pudans.extension.contract.SchemeManager;
import cn.tolim.pudans.extension.event.SchemeRemovedEvent;
import cn.tolim.pudans.extension.exception.ExtensionConvertException;
import cn.tolim.pudans.extension.exception.SchemaViolationException;
import cn.tolim.pudans.infra.r2dbc.store.ExtensionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openapi4j.core.exception.ResolutionException;
import org.openapi4j.core.model.v3.OAI3;
import org.openapi4j.core.model.v3.OAI3Context;
import org.openapi4j.schema.validator.ValidationContext;
import org.openapi4j.schema.validator.ValidationData;
import org.openapi4j.schema.validator.v3.SchemaValidator;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.Exceptions;


@Slf4j
@Component
class JSONExtensionConverter implements ExtensionConverter {

    private final SchemeManager schemeManager;
    private final ConcurrentMap<Scheme, SchemaValidator> validatorMap = new ConcurrentHashMap<>();
    @Getter
    public ObjectMapper objectMapper;

    public JSONExtensionConverter(SchemeManager schemeManager) {
        this.schemeManager = schemeManager;
        setObjectMapper(OBJECT_MAPPER);
    }


    void setObjectMapper(ObjectMapper objectMapper) {
        Assert.notNull(objectMapper, "ObjectMapper 不能为 null");
        this.objectMapper = objectMapper;
    }

    @Override
    public <E extends Extension> ExtensionStore convertTo(E extension) {
        var gvk = extension.groupVersionKind();
        var scheme = schemeManager.get(gvk);

        try {
            var convertedExtension = Optional.of(extension)
                    .map(item -> scheme.type().isAssignableFrom(item.getClass())
                            ? item
                            : objectMapper.convertValue(item, scheme.type()))
                    .orElseThrow();
            var validation = new ValidationData<>(extension);

            var extensionJsonNode = objectMapper.valueToTree(convertedExtension);
            var validator = getValidator(scheme);
            validator.validate(extensionJsonNode, validation);
            if (!validation.isValid()) {
                log.debug(
                        "扩展校验失败：{}，错误信息：{}",
                        extension.getClass(),
                        validation.results());
                throw new SchemaViolationException(extension.groupVersionKind(), validation.results());
            }

            var version = extension.getMetadata().getVersion();
            var storeName = buildStoreName(scheme, extension.getMetadata().getName());
            var data = objectMapper.writeValueAsBytes(extensionJsonNode);
            return new ExtensionStore(storeName, data, version);
        } catch (IOException e) {
            throw new ExtensionConvertException("无法将扩展写入为字节", e);
        } catch (ResolutionException e) {
            throw new RuntimeException("创建 Schema 校验器失败", e);
        }
    }

    @Override
    public <E extends Extension> E convertFrom(Class<E> type, ExtensionStore extensionStore) {
        try {
            var extension = objectMapper.readValue(extensionStore.getData(), type);
            extension.getMetadata().setVersion(extensionStore.getVersion());
            return extension;
        } catch (IOException e) {
            throw new ExtensionConvertException("无法从字节读取扩展 " + type + " 失败", e);
        }
    }

    @EventListener
    void onSchemeRemovedEvent(SchemeRemovedEvent event) {
        var removed = validatorMap.remove(event.getScheme());
        if (log.isDebugEnabled()) {
            if (removed == null) {
                log.debug(
                        "移除方案 {} 的校验器时未找到可用的校验器",
                        event.getScheme().groupVersionKind());
            } else {
                log.debug(
                        "已移除数据结构校验器 {}，方案：{}",
                        removed,
                        event.getScheme().groupVersionKind());
            }
        }
    }

    private SchemaValidator getValidator(Scheme scheme) throws MalformedURLException, ResolutionException {
        return validatorMap.computeIfAbsent(scheme, s -> {
            try {
                var context = new ValidationContext<OAI3>(new OAI3Context(new URL("file:/"), scheme.openApiSchema()));
                context.setFastFail(false);
                var validator = new SchemaValidator(context, null, scheme.openApiSchema());
                if (log.isDebugEnabled()) {
                    log.debug("已创建数据结构校验器 {}，方案：{}", validator, scheme.groupVersionKind());
                }
                return validator;
            } catch (ResolutionException | MalformedURLException e) {
                throw Exceptions.propagate(e);
            }
        });
    }
}
