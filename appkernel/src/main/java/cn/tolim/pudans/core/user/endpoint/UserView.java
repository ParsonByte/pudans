package cn.tolim.pudans.core.user.endpoint;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.MetadataOperator;
import java.time.Instant;


public record UserView(String apiVersion, String kind, MetadataOperator metadata, Spec spec, Status status) {

    // 默认暴露邮箱生成视图
    public static UserView from(User user) {
        return from(user, true);
    }

    // 按可见性决定是否输出邮箱
    public static UserView from(User user, boolean exposeEmail) {
        if (user == null) {
            return null;
        }
        var userSpec = user.getSpec();
        var spec = new Spec(
                userSpec.getUid(),
                userSpec.getDisplayName(),
                exposeEmail ? userSpec.getEmail() : null,
                userSpec.getAvatar(),
                userSpec.getPhone(),
                userSpec.getBio(),
                userSpec.isEmailVerified(),
                userSpec.getRegisteredAt(),
                userSpec.getDisabled(),
                userSpec.getTwoFactorAuthEnabled());
        var status = new Status(
                user.getStatus() == null ? null : user.getStatus().getPermalink(),
                user.getStatus() == null ? null : user.getStatus().getHashId());
        return new UserView("pudans.tolim.cn/v1alpha1", User.KIND, user.getMetadata(), spec, status);
    }


    public record Spec(
            String uid,
            String displayName,
            String email,
            String avatar,
            String phone,
            String bio,
            boolean emailVerified,
            Instant registeredAt,
            Boolean disabled,
            Boolean twoFactorAuthEnabled) {}


    public record Status(String permalink, String hashId) {}
}
