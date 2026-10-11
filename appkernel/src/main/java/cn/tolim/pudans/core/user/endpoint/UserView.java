package cn.tolim.pudans.core.user.endpoint;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.MetadataOperator;
import java.time.Instant;


public record UserView(String apiVersion, String kind, MetadataOperator metadata, Spec spec, Status status) {

    public static UserView from(User user) {
        if (user == null) {
            return null;
        }
        var userSpec = user.getSpec();
        var spec = new Spec(
                userSpec.getUid(),
                userSpec.getDisplayName(),
                userSpec.getEmail(),
                userSpec.getAvatar(),
                userSpec.getPhone(),
                userSpec.getBio(),
                userSpec.isEmailVerified(),
                userSpec.getRegisteredAt(),
                userSpec.getDisabled(),
                userSpec.getTwoFactorAuthEnabled());
        var status = new Status(user.getStatus() == null ? null : user.getStatus().getPermalink());
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


    public record Status(String permalink) {}
}
