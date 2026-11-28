package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.communal.jackjson.encrypt.JsonPropertyEncrypt;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;

import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_CONNECT_TIMEOUT;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_GROUP_FILTER;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_GROUP_MEMBER_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_GROUP_NAME_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_MEMBER_OF_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_PAGE_SIZE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_READ_TIMEOUT;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_REFERRAL;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_ROOT_DEPT_ID;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_ROOT_DEPT_NAME;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_DISPLAY_NAME_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_EMAIL_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_FILTER;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_ID_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_NAME_ATTRIBUTE;
import static cn.tolim.pudans.iam.identitysource.ldap.LdapConstants.DEFAULT_USER_PHONE_ATTRIBUTE;


@Getter
@Setter
public class LdapConfig extends IdentitySourceConfig {

    @Serial
    private static final long serialVersionUID = 5237116550358919678L;

    // LDAP server URL, for example ldap://127.0.0.1:389 or ldaps://127.0.0.1:636
    @NotEmpty(message = "LDAP server URL cannot be empty")
    private String serverUrl;

    // Base DN used by all searches
    @NotEmpty(message = "LDAP base DN cannot be empty")
    private String baseDn;

    // Service account used to bind to the directory, anonymous bind when blank
    private String bindDn;

    // Service account password
    @JsonPropertyEncrypt
    private String bindPassword;

    // Optional user search base, falls back to baseDn
    private String userSearchBase;

    // Optional group search base, falls back to baseDn
    private String groupSearchBase;

    // User search filter, must select user entries
    private String userFilter = DEFAULT_USER_FILTER;

    // Group search filter, must select group entries
    private String groupFilter = DEFAULT_GROUP_FILTER;

    // Attribute holding the immutable user identifier
    private String userIdAttribute = DEFAULT_USER_ID_ATTRIBUTE;

    // Attribute holding the user full name
    private String userNameAttribute = DEFAULT_USER_NAME_ATTRIBUTE;

    // Attribute holding the user display name
    private String userDisplayNameAttribute = DEFAULT_USER_DISPLAY_NAME_ATTRIBUTE;

    // Attribute holding the user email address
    private String userEmailAttribute = DEFAULT_USER_EMAIL_ATTRIBUTE;

    // Attribute holding the user phone number
    private String userPhoneAttribute = DEFAULT_USER_PHONE_ATTRIBUTE;

    // Optional attribute holding the user avatar URL
    private String userAvatarAttribute;

    // Optional attribute holding the user enabled flag
    private String userActiveAttribute;

    // Attribute holding the groups a user belongs to
    private String memberOfAttribute = DEFAULT_MEMBER_OF_ATTRIBUTE;

    // Attribute holding the group name
    private String groupNameAttribute = DEFAULT_GROUP_NAME_ATTRIBUTE;

    // Attribute holding the group member DNs
    private String groupMemberAttribute = DEFAULT_GROUP_MEMBER_ATTRIBUTE;

    // Whether to expose a synthetic root department so users without groups are pulled
    private Boolean includeRootDept = Boolean.TRUE;

    // Synthetic root department identifier
    private String rootDeptId = DEFAULT_ROOT_DEPT_ID;

    // Synthetic root department name
    private String rootDeptName = DEFAULT_ROOT_DEPT_NAME;

    // Paged search size, 0 disables paged results
    @Min(value = 0, message = "LDAP page size cannot be negative")
    @Max(value = 10000, message = "LDAP page size cannot exceed 10000")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    // Connection timeout in milliseconds
    @Min(value = 1, message = "LDAP connect timeout must be greater than zero")
    private Integer connectTimeout = DEFAULT_CONNECT_TIMEOUT;

    // Read timeout in milliseconds
    @Min(value = 1, message = "LDAP read timeout must be greater than zero")
    private Integer readTimeout = DEFAULT_READ_TIMEOUT;

    // Whether to start TLS after connecting
    private Boolean startTls = Boolean.FALSE;

    // Referral handling, ignore or follow
    private String referral = DEFAULT_REFERRAL;

    // Optional fixed area code applied to mapped phone numbers
    private String phoneAreaCode;

    public LdapConfig() {
    }

    // Effective user search base
    public String getUserBaseDn() {
        return StringUtils.defaultIfBlank(userSearchBase, baseDn);
    }

    // Effective group search base
    public String getGroupBaseDn() {
        return StringUtils.defaultIfBlank(groupSearchBase, baseDn);
    }

    // Effective user filter
    public String getUserSearchFilter() {
        return StringUtils.defaultIfBlank(userFilter, DEFAULT_USER_FILTER);
    }

    // Effective group filter
    public String getGroupSearchFilter() {
        return StringUtils.defaultIfBlank(groupFilter, DEFAULT_GROUP_FILTER);
    }

    // Effective root department identifier
    public String getEffectiveRootDeptId() {
        return StringUtils.defaultIfBlank(rootDeptId, DEFAULT_ROOT_DEPT_ID);
    }

    // Effective root department name
    public String getEffectiveRootDeptName() {
        return StringUtils.defaultIfBlank(rootDeptName, DEFAULT_ROOT_DEPT_NAME);
    }

    // Whether the synthetic root department is enabled
    public boolean isRootDeptEnabled() {
        return !Boolean.FALSE.equals(includeRootDept);
    }

    // Whether StartTLS is enabled
    public boolean isStartTlsEnabled() {
        return Boolean.TRUE.equals(startTls);
    }
}