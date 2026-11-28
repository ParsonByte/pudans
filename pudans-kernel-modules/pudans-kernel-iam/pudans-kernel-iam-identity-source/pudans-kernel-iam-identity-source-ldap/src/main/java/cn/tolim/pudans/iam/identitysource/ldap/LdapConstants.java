package cn.tolim.pudans.iam.identitysource.ldap;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LdapConstants {

    public static final String LOGGER_NAME = "ldap-identity";

    // Default LDAP connection and search values
    public static final int DEFAULT_PAGE_SIZE = 500;
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    public static final int DEFAULT_READ_TIMEOUT = 10000;
    public static final String DEFAULT_REFERRAL = "ignore";

    // Default object class filters
    public static final String DEFAULT_USER_FILTER = "(objectClass=person)";
    public static final String DEFAULT_GROUP_FILTER = "(objectClass=groupOfNames)";

    // Default user attribute mapping
    public static final String DEFAULT_USER_ID_ATTRIBUTE = "uid";
    public static final String DEFAULT_USER_NAME_ATTRIBUTE = "cn";
    public static final String DEFAULT_USER_DISPLAY_NAME_ATTRIBUTE = "displayName";
    public static final String DEFAULT_USER_EMAIL_ATTRIBUTE = "mail";
    public static final String DEFAULT_USER_PHONE_ATTRIBUTE = "telephoneNumber";
    public static final String DEFAULT_MEMBER_OF_ATTRIBUTE = "memberOf";

    // Default group attribute mapping
    public static final String DEFAULT_GROUP_NAME_ATTRIBUTE = "cn";
    public static final String DEFAULT_GROUP_MEMBER_ATTRIBUTE = "member";

    // Synthetic root department used to pull users that do not belong to any group
    public static final String DEFAULT_ROOT_DEPT_ID = "ldap-root";
    public static final String DEFAULT_ROOT_DEPT_NAME = "LDAP";

    // Event callback result
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_MESSAGE = "message";
    public static final String EVENT_STATUS_IGNORED = "ignored";
}