package cn.tolim.pudans.extension.contract;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(group = "", version = "v1alpha1", kind = Secret.KIND, plural = "secrets", singular = "secret")
public class Secret extends AbstractExtension {
    public static final String KIND = "Secret";


    private String type;


    private Map<String, byte[]> data;


    private Map<String, String> stringData;
}
