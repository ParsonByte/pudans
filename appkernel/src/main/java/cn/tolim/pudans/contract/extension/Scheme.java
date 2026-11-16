package cn.tolim.pudans.contract.extension;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;


public record Scheme(
        Class<? extends Extension> type,
        GroupVersionKind groupVersionKind,
        String plural,
        String singular,
        ObjectNode openApiSchema) {
    public Scheme {
        Assert.notNull(type, "扩展的类型不能为 null");
        Assert.notNull(groupVersionKind, "扩展的 GroupVersionKind 不能为 null");
        Assert.hasText(plural, "扩展的复数名称不能为空");
        Assert.hasText(singular, "扩展的单数名称不能为空");
        Assert.notNull(openApiSchema, "JSON Schema 不能为 null");
    }


    public static Scheme buildFromType(Class<? extends Extension> type) {

        var gvk = getGvkFromType(type);


        var resolvedSchema = ModelConverters.getInstance().readAllAsResolvedSchema(type);
        var mapper = Json.mapper();
        var schema = (ObjectNode) mapper.valueToTree(resolvedSchema.schema);

        schema.set("components", mapper.valueToTree(Map.of("schemas", resolvedSchema.referencedSchemas)));

        return new Scheme(
                type,
                new GroupVersionKind(gvk.group(), gvk.version(), gvk.kind()),
                gvk.plural(),
                gvk.singular(),

                schema.deepCopy());
    }


    public static GVK getGvkFromType(Class<? extends Extension> type) {
        var gvk = type.getAnnotation(GVK.class);
        Assert.notNull(gvk, "缺少注解 " + GVK.class.getName() + "，类型为 " + type.getName());
        return gvk;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Scheme scheme = (Scheme) o;
        return groupVersionKind.equals(scheme.groupVersionKind);
    }

    @Override
    public int hashCode() {
        return groupVersionKind.hashCode();
    }
}
