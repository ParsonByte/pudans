package cn.tolim.pudans.infra.setting;

import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;

@ExtendWith(MockitoExtension.class)
class ParameterSettingSessionTimeoutApplierTest {

    @Mock
    ParameterSettingGroupService parameterSettingGroupService;

    @Mock
    ReactiveRedisIndexedSessionRepository sessionRepository;

    @Test
    void appliesTheConfiguredAccessTokenTtlToNewSessions() {
        var applier = new ParameterSettingSessionTimeoutApplier(
                parameterSettingGroupService, sessionRepository);

        applier.apply(900);

        verify(sessionRepository).setDefaultMaxInactiveInterval(Duration.ofSeconds(900));
    }

    @Test
    void keepsSpringSessionTimeoutWhenTheValueIsMissing() {
        var applier = new ParameterSettingSessionTimeoutApplier(
                parameterSettingGroupService, sessionRepository);

        applier.apply(null);

        org.mockito.Mockito.verifyNoInteractions(sessionRepository);
    }
}
