package cn.tolim.pudans.iam.identitysource.core;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceEventPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncDeptPostProcessor;
import cn.tolim.pudans.iam.identitysource.core.processor.IdentitySourceSyncUserPostProcessor;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractDefaultIdentitySourceTests {

    private final IdentitySourceClient client = mock(IdentitySourceClient.class);
    private final IdentitySourceSyncUserPostProcessor userPostProcessor =
            mock(IdentitySourceSyncUserPostProcessor.class);
    private final IdentitySourceSyncDeptPostProcessor deptPostProcessor =
            mock(IdentitySourceSyncDeptPostProcessor.class);
    private final IdentitySourceEventPostProcessor eventPostProcessor =
            mock(IdentitySourceEventPostProcessor.class);
    private final TestIdentitySource identitySource = new TestIdentitySource("source-1", "Source",
            new IdentitySourceConfig(), client, userPostProcessor, deptPostProcessor,
            eventPostProcessor);

    private static Dept dept(String id) {
        Dept dept = new Dept();
        dept.setDeptId(id);
        dept.setName("Dept " + id);
        return dept;
    }

    private static User user(String id) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(id + "@example.com");
        return user;
    }

    @Test
    void shouldPullDepartmentsAndDistinctUsers() {
        Dept first = dept("d1");
        Dept second = dept("d2");
        User firstUser = user("u1");
        User sharedUser = user("u2");
        User lastUser = user("u3");
        when(client.getDeptList()).thenReturn(List.of(first, second));
        when(client.getUserList(first)).thenReturn(List.of(firstUser, sharedUser));
        when(client.getUserList(second)).thenReturn(List.of(sharedUser, lastUser));

        identitySource.sync(TriggerType.MANUAL);

        ArgumentCaptor<String> deptBatch = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userBatch = ArgumentCaptor.forClass(String.class);
        verify(deptPostProcessor).process(deptBatch.capture(), eq("source-1"),
                eq(List.of(first, second)), any(LocalDateTime.class), eq(TriggerType.MANUAL));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<User>> usersCaptor = ArgumentCaptor.forClass(List.class);
        verify(userPostProcessor).process(userBatch.capture(), eq("source-1"),
                usersCaptor.capture(), any(LocalDateTime.class), eq(TriggerType.MANUAL));
        assertEquals(deptBatch.getValue(), userBatch.getValue());
        assertEquals(7, deptBatch.getValue().length());
        assertEquals(List.of(firstUser, sharedUser, lastUser), usersCaptor.getValue());
    }

    @Test
    void shouldPassCallbackRequestAndBodyToProvider() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        Object result = identitySource.event(request, "{\"event\":\"ok\"}");

        assertSame(TestIdentitySource.EVENT_RESULT, result);
        assertSame(request, identitySource.lastRequest);
        assertEquals("{\"event\":\"ok\"}", identitySource.lastBody);
    }

    @Test
    void shouldExposeIdentityAndConfiguration() {
        assertEquals("source-1", identitySource.getId());
        assertEquals("Source", identitySource.getName());
        assertSame(identitySource.getConfig(), identitySource.getConfig());
    }

    // Identity source assembled only for the base class tests
    private static final class TestIdentitySource
            extends AbstractDefaultIdentitySource<IdentitySourceConfig> {

        private static final Object EVENT_RESULT = Map.of("event", "ok");

        private HttpServletRequest lastRequest;
        private String lastBody;

        private TestIdentitySource(String id, String name, IdentitySourceConfig config,
                                   IdentitySourceClient client,
                                   IdentitySourceSyncUserPostProcessor userPostProcessor,
                                   IdentitySourceSyncDeptPostProcessor deptPostProcessor,
                                   IdentitySourceEventPostProcessor eventPostProcessor) {
            super(id, name, config, client, userPostProcessor, deptPostProcessor,
                    eventPostProcessor);
        }

        @Override
        public Object event(HttpServletRequest request, String body) {
            this.lastRequest = request;
            this.lastBody = body;
            return EVENT_RESULT;
        }
    }
}