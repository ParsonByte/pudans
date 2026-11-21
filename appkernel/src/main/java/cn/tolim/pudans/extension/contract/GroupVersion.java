package cn.tolim.pudans.extension.contract;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;


public record GroupVersion(String group, String version) {

    // 解析 group/version，单段字符串视为核心组。
    public static GroupVersion parseAPIVersion(String apiVersion) {
        Assert.hasText(apiVersion, "API 版本不能为空");

        var groupVersion = apiVersion.split("/");
        return switch (groupVersion.length) {
            case 1 -> new GroupVersion("", apiVersion);
            case 2 -> new GroupVersion(groupVersion[0], groupVersion[1]);
            default -> throw new IllegalArgumentException("无法识别的 apiVersion 字符串：" + apiVersion);
        };
    }

    @Override
    public String toString() {
        return StringUtils.hasText(group) ? group + "/" + version : version;
    }
}
