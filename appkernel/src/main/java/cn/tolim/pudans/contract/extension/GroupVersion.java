package cn.tolim.pudans.contract.extension;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public record GroupVersion(String group, String version) {

    public static GroupVersion parseAPIVersion(String apiVersion) {
        Assert.hasText(apiVersion, "API version must not be blank");

        var groupVersion = apiVersion.split("/");
        return switch (groupVersion.length) {
            case 1 -> new GroupVersion("", apiVersion);
            case 2 -> new GroupVersion(groupVersion[0], groupVersion[1]);
            default -> throw new IllegalArgumentException("Unexpected APIVersion string: " + apiVersion);
        };
    }

    @Override
    public String toString() {
        return StringUtils.hasText(group) ? group + "/" + version : version;
    }
}
