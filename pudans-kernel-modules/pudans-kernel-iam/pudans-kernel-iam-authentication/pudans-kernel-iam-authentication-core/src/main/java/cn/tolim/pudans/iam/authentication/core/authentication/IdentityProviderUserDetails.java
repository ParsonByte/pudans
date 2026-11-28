package cn.tolim.pudans.iam.authentication.core.authentication;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Getter;

import java.util.Map;


@Getter
public class IdentityProviderUserDetails {

    // Personal email
    private final String email;

    // Phone number
    private final String mobile;

    // Nickname
    private final String nickName;

    // Avatar URL
    private final String avatarUrl;

    // openId
    private final String openId;

    // Country code for the phone number
    private final String stateCode;
    // providerId
    private final String providerId;
    // providerCode
    private final String providerCode;
    // providerType
    private final IdentityProviderType providerType;
    // Additional configuration
    private final Map<String, Object> additionalInfo;
    // unionId
    public String unionId;

    IdentityProviderUserDetails(final String email, final String mobile, final String nickName,
                                final String avatarUrl, final String openId, final String stateCode,
                                final String unionId, final String providerId,
                                final String providerCode, final IdentityProviderType providerType,
                                final Map<String, Object> additionalInfo) {
        if (providerId == null) {
            throw new NullPointerException("providerId 不能为空");
        } else if (providerType == null) {
            throw new NullPointerException("providerType 不能为空");
        } else {
            this.email = email;
            this.mobile = mobile;
            this.nickName = nickName;
            this.avatarUrl = avatarUrl;
            this.openId = openId;
            this.stateCode = stateCode;
            this.unionId = unionId;
            this.providerId = providerId;
            this.providerType = providerType;
            this.providerCode = providerCode;
            this.additionalInfo = additionalInfo;
        }
    }

    public static IdpUserDetailsBuilder builder() {
        return new IdpUserDetailsBuilder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class IdpUserDetailsBuilder {

        private String email;

        private String mobile;

        private String nickName;

        private String avatarUrl;

        private String openId;

        private String stateCode;

        private String unionId;

        private String providerId;

        private String providerCode;

        private IdentityProviderType providerType;

        private Map<String, Object> additionalInfo;

        IdpUserDetailsBuilder() {
        }

        public IdpUserDetailsBuilder email(final String email) {
            this.email = email;
            return this;
        }

        public IdpUserDetailsBuilder mobile(final String mobile) {
            this.mobile = mobile;
            return this;
        }

        public IdpUserDetailsBuilder nickName(final String nickName) {
            this.nickName = nickName;
            return this;
        }

        public IdpUserDetailsBuilder avatarUrl(final String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public IdpUserDetailsBuilder openId(final String openId) {
            this.openId = openId;
            return this;
        }

        public IdpUserDetailsBuilder stateCode(final String stateCode) {
            this.stateCode = stateCode;
            return this;
        }

        public IdpUserDetailsBuilder unionId(final String unionId) {
            this.unionId = unionId;
            return this;
        }

        public IdpUserDetailsBuilder providerId(final String providerId) {
            if (providerId == null) {
                throw new NullPointerException("providerId 不能为空");
            } else {
                this.providerId = providerId;
                return this;
            }
        }

        public IdpUserDetailsBuilder providerCode(final String providerCode) {
            if (providerCode == null) {
                throw new NullPointerException("providerCode 不能为空");
            } else {
                this.providerCode = providerCode;
                return this;
            }
        }

        public IdpUserDetailsBuilder providerType(final IdentityProviderType providerType) {
            if (providerType == null) {
                throw new NullPointerException("providerType 不能为空");
            } else {
                this.providerType = providerType;
                return this;
            }
        }

        public IdpUserDetailsBuilder additionalInfo(final Map<String, Object> additionalInfo) {
            this.additionalInfo = additionalInfo;
            return this;
        }

        public IdentityProviderUserDetails build() {
            return new IdentityProviderUserDetails(this.email, this.mobile, this.nickName,
                    this.avatarUrl, this.openId, this.stateCode, this.unionId, this.providerId,
                    this.providerCode, this.providerType, this.additionalInfo);
        }

    }
}
