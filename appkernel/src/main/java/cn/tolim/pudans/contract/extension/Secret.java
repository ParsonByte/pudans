package cn.tolim.pudans.contract.extension;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "", version = "v1alpha1", kind = Secret.KIND, plural = "secrets", singular = "secret")
public class Secret extends AbstractExtension {
    public static final String KIND = "Secret";

    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    public static final int MAX_SECRET_SIZE = 1024 * 1024;


    private String type;


    private Map<String, byte[]> data;


    private Map<String, String> stringData;
}
