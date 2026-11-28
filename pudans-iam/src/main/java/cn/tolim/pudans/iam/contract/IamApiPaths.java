package cn.tolim.pudans.iam.contract;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

public final class IamApiPaths {

    public static final String BASE = IamConstants.API_PREFIX;

    public static final String ACTUATOR = BASE + "/actuator";

    public static final String HEALTH = ACTUATOR + "/health";

    public static final String INFO = ACTUATOR + "/info";

    public static final String ERROR = BASE + "/error";

    public static final String LOGIN = BASE + "/auth/login";

    private IamApiPaths() {
    }
}
