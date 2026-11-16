package cn.tolim.pudans.contract.extension;

import java.util.Set;

// pudans HTTP 契约唯一允许的 API 组。
public final class ApiGroups {

    public static final String API = "api.pudans.tolim.cn";

    public static final String VERSION = "v1alpha1";

    public static final String API_ROOT = "/apis/" + API;

    public static final String API_VERSION = API + "/" + VERSION;

    public static final String API_PREFIX = "/apis/" + API_VERSION;

    public static final Set<String> ALL = Set.of(API);

    public static final String SETUP_PATH = API_PREFIX + "/system/setup";

    private ApiGroups() {}
}
