package cn.tolim.pudans.extension.api;

import lombok.Data;


@Data
public abstract class AbstractExtension implements Extension {

    private String apiVersion;

    private String kind;

    private MetadataOperator metadata;

    @Override
    public String getApiVersion() {
        var apiVersionFromGvk = Extension.super.getApiVersion();
        return apiVersionFromGvk != null ? apiVersionFromGvk : this.apiVersion;
    }

    @Override
    public String getKind() {
        var kindFromGvk = Extension.super.getKind();
        return kindFromGvk != null ? kindFromGvk : this.kind;
    }
}
