package cn.tolim.pudans.core.ai.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.infra.exception.ApiException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiQuotaGuardTest {

    private final AiQuotaGuard guard = new AiQuotaGuard();

    @Test
    void allowsTheQuotaManagementRoleAndSuperRole() {
        assertThatCode(() -> guard.requireInspection(Set.of(AiQuotaGuard.MANAGE_ROLE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.requireInspection(Set.of("super-role")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAUserWhoCanOnlyReadTheOwnSnapshot() {
        assertThatThrownBy(() -> guard.requireInspection(Set.of("role-template-view-ai-quota")))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(403);
                    assertThat(error.getCode()).isEqualTo("ai-quota-inspection-forbidden");
                });
    }
}
