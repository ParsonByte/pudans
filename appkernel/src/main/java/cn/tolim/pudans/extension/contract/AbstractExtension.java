package cn.tolim.pudans.extension.contract;

import lombok.Data;


@Data
public abstract class AbstractExtension implements Extension {

    private String apiVersion;

    private String kind;

    private MetadataOperator metadata;

    // 优先采用 GVK 注解推导的版本，缺省回落字段值。
    @Override
    public String getApiVersion() {
        var apiVersionFromGvk = Extension.super.getApiVersion();
        return apiVersionFromGvk != null ? apiVersionFromGvk : this.apiVersion;
    }

    // 优先采用 GVK 注解推导的 Kind，缺省回落字段值。
    @Override
    public String getKind() {
        var kindFromGvk = Extension.super.getKind();
        return kindFromGvk != null ? kindFromGvk : this.kind;
    }
}
