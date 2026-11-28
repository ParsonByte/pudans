package cn.tolim.pudans.iam.identitysource.feishu.domain.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;

// user
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUserResponse implements Serializable {
    // User union_id
    @JsonProperty("union_id")
    private String unionId;
    // Unique user identifier in the tenant, user_id
    @JsonProperty("user_id")
    private String userId;
    // User open_id
    @JsonProperty("open_id")
    private String openId;
    // Username
    @JsonProperty("name")
    private String name;
    // English name
    @JsonProperty("en_name")
    private String enName;
    // Alias
    private String nickname;
    // Email
    private String email;
    // Phone number
    private String mobile;
    // Phone visibility; true means visible and false means hidden, default true. Hidden phone numbers are inaccessible to organization members.
    @JsonProperty("mobile_visible")
    private boolean mobileVisible;
    // Gender
    // Available values:
    // 0: Private
    // 1: Male
    // 2: Female
    private int gender;
    // User avatar information
    private Avatar avatar;
    // User status
    private Status status;
    // Department IDs for the user; a user may belong to multiple departments.
    // The ID value corresponds to department_id_type in the query parameters.
    @JsonProperty("department_ids")
    private List<String> departmentIds;
    // User ID of the direct manager; the value corresponds to user_id_type in the query parameters.
    @JsonProperty("leader_user_id")
    private String leaderUserId;
    // City
    private String city;
    // Country or region code; see the documented format
    // https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/contact-v3/user/country-code-description
    private String country;
    // Work station
    @JsonProperty("work_station")
    private String workStation;
    // Hire date
    @JsonProperty("join_time")
    private int joinTime;
    // Whether the user is a tenant super administrator
    @JsonProperty("is_tenant_manager")
    private boolean isTenantManager;
    // Enterprise email; enable Feishu email in the admin console first
    @JsonProperty("enterprise_email")
    private String enterpriseEmail;
    // Job title
    @JsonProperty("job_title")
    private String jobTitle;

    // avatar
    @Data
    public static class Avatar implements Serializable {
        @JsonProperty("avatar_72")
        private String avatar72;
        @JsonProperty("avatar_240")
        private String avatar240;
        @JsonProperty("v")
        private String avatar640;
        @JsonProperty("avatar_origin")
        private String avatarOrigin;

        public String getAvatar() {
            return StringUtils.firstNonEmpty(avatar72, avatar240, avatar640, avatarOrigin);
        }
    }

    // User status
    @Data
    public static class Status implements Serializable {
        // Suspended
        @JsonProperty("is_frozen")
        private boolean isFrozen;
        // Departed
        @JsonProperty("is_resigned")
        private boolean isResigned;
        // Activated
        @JsonProperty("is_activated")
        private boolean isActivated;
        // Whether the user left voluntarily; after some time, the user automatically becomes departed

        private boolean isExited;
        // Whether the user has not joined and must confirm to join the team
        @JsonProperty("is_unjoin")
        private boolean isUnJoin;

        public boolean isActive() {
            return isActivated && !isFrozen && !isResigned && !isExited && !isUnJoin;
        }
    }
}
