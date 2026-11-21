package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.Metadata;
import org.junit.jupiter.api.Test;

class UserViewEmailVisibilityTest {

    @Test
    void hidesTheEmailWhenTheSettingIsDisabled() {
        var user = user();

        assertThat(UserView.from(user, false).spec().email()).isNull();
        assertThat(UserEndpoint.ListedUser.from(user, false).email()).isNull();
    }

    @Test
    void exposesTheEmailWhenTheSettingIsEnabled() {
        var user = user();

        assertThat(UserView.from(user, true).spec().email()).isEqualTo("alice@example.com");
        assertThat(UserEndpoint.ListedUser.from(user, true).email()).isEqualTo("alice@example.com");
    }

    @Test
    void theDefaultViewKeepsTheEmailForCompatibility() {
        assertThat(UserView.from(user()).spec().email()).isEqualTo("alice@example.com");
        assertThat(UserEndpoint.ListedUser.from(user()).email()).isEqualTo("alice@example.com");
    }

    @Test
    void listedUsersExposeTheUserGroupNameAndDisplayName() {
        var group = new UserGroup();
        var metadata = new Metadata();
        metadata.setName("admins");
        group.setMetadata(metadata);
        group.setDisplayName("管理员");

        var listed = UserEndpoint.ListedUser.from(user(), true, group);

        assertThat(listed.userGroup()).isEqualTo("admins");
        assertThat(listed.userGroupDisplayName()).isEqualTo("管理员");
    }

    private static User user() {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName("alice");
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setUid("uid-1");
        spec.setDisplayName("Alice");
        spec.setEmail("alice@example.com");
        user.setSpec(spec);
        return user;
    }
}
