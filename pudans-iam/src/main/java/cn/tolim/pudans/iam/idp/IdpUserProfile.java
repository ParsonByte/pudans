package cn.tolim.pudans.iam.idp;

// User profile returned by an external identity provider
public record IdpUserProfile(
        String openId,
        String unionId,
        String email,
        String mobile,
        String nickName,
        String avatarUrl) {
}