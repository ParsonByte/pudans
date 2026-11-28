package cn.tolim.pudans.iam.core.auth;

import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

public final class IamAuthTypes {

    // Session user type for business users
    public static final String USER = "USER";

    // Session user type for administrators
    public static final String ADMIN = "ADMIN";

    // Session cookie path attribute
    public static final String SESSION_ATTRIBUTE = "PUDANS_IAM_SESSION";

    // Header used by service-to-service callers to pass the global session id
    public static final String SESSION_HEADER = "X-Pudans-Session-Id";

    private IamAuthTypes() {
    }

    // Resolves the session user type from the Support user type
    // Parameter userType: UserType
    // Returns: String
    public static String of(UserType userType) {
        return UserType.ADMIN.equals(userType) ? ADMIN : USER;
    }

    // Resolves the Support user type from a session user type
    // Parameter userType: String
    // Returns: UserType
    public static UserType toUserType(String userType) {
        return ADMIN.equalsIgnoreCase(userType) ? UserType.ADMIN : UserType.USER;
    }

    // Checks whether the given session user type is the administrator type
    // Parameter userType: String
    // Returns: boolean
    public static boolean isAdmin(String userType) {
        return userType != null && ADMIN.equalsIgnoreCase(userType);
    }
}