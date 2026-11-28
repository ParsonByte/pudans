package cn.tolim.pudans.iam.identitysource.scim.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScimUser extends ScimResource {

    // Unique user name
    private String userName;

    // Structured name
    private ScimName name;

    // Display name
    private String displayName;

    // Nick name
    private String nickName;

    // Profile URL
    private String profileUrl;

    // Title
    private String title;

    // User type
    private String userType;

    // Preferred language
    private String preferredLanguage;

    // Locale
    private String locale;

    // Time zone
    private String timezone;

    // Whether the user is active
    private Boolean active;

    // Password, write only
    private String password;

    // Email addresses
    private List<ScimMultiValue> emails;

    // Phone numbers
    private List<ScimMultiValue> phoneNumbers;

    // Photos
    private List<ScimMultiValue> photos;

    // Groups the user belongs to
    private List<ScimGroupRef> groups;
}