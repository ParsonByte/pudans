package cn.tolim.pudans.iam.synchronizer.endpoint;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.repository.identitysource.IdentitySourceRepository;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.synchronizer.configuration.IdentitySourceBeanUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentitySourceEventReceiveEndpointTests {

    private ApplicationContext applicationContext;
    private IdentitySourceRepository identitySourceRepository;
    private IdentitySourceEventReceiveEndpoint endpoint;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        identitySourceRepository = mock(IdentitySourceRepository.class);
        endpoint = new IdentitySourceEventReceiveEndpoint(applicationContext,
                identitySourceRepository);
    }

    private static IdentitySourceEntity entity(boolean enabled, boolean configured) {
        IdentitySourceEntity entity = new IdentitySourceEntity();
        entity.setId("source-1");
        entity.setName("Source");
        entity.setCode("source-code");
        entity.setEnabled(enabled);
        entity.setConfigured(configured);
        return entity;
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldDelegateCallbackToEnabledIdentitySource() {
        IdentitySourceEntity entity = entity(true, true);
        IdentitySource<IdentitySourceConfig> identitySource = mock(IdentitySource.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Map<String, Object> payload = Map.of("received", true);
        when(identitySourceRepository.findByCode("source-code"))
                .thenReturn(Optional.of(entity));
        when(applicationContext.getBean(
                IdentitySourceBeanUtils.getSourceBeanName("source-1")))
                .thenReturn(identitySource);
        when(identitySource.event(request, "{\"event\":\"user_add\"}")).thenReturn(payload);

        ResponseEntity<?> response = endpoint.receive(request, "source-code",
                "{\"event\":\"user_add\"}");

        assertEquals(200, response.getStatusCode().value());
        assertSame(payload, response.getBody());
        verify(identitySource).event(request, "{\"event\":\"user_add\"}");
    }

    @Test
    void shouldIgnoreUnknownIdentitySource() {
        when(identitySourceRepository.findByCode("missing")).thenReturn(Optional.empty());

        ResponseEntity<?> response =
                endpoint.receive(mock(MockHttpServletRequest.class), "missing", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(null, response.getBody());
        verifyNoInteractions(applicationContext);
    }

    @Test
    void shouldIgnoreDisabledIdentitySource() {
        when(identitySourceRepository.findByCode("source-code"))
                .thenReturn(Optional.of(entity(false, true)));

        ResponseEntity<?> response =
                endpoint.receive(mock(MockHttpServletRequest.class), "source-code", "body");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(null, response.getBody());
        verifyNoInteractions(applicationContext);
    }

    @Test
    void shouldIgnoreUnconfiguredIdentitySource() {
        when(identitySourceRepository.findByCode("source-code"))
                .thenReturn(Optional.of(entity(true, false)));

        ResponseEntity<?> response =
                endpoint.receive(mock(MockHttpServletRequest.class), "source-code", "body");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(null, response.getBody());
        verifyNoInteractions(applicationContext);
    }

    @Test
    void shouldAcceptCallbackWithoutBody() {
        IdentitySourceEntity entity = entity(true, true);
        IdentitySource<IdentitySourceConfig> identitySource = mock(IdentitySource.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(identitySourceRepository.findByCode("source-code"))
                .thenReturn(Optional.of(entity));
        when(applicationContext.getBean(
                IdentitySourceBeanUtils.getSourceBeanName("source-1")))
                .thenReturn(identitySource);
        when(identitySource.event(request, null)).thenReturn("ok");

        ResponseEntity<?> response = endpoint.receive(request, "source-code", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("ok", response.getBody());
    }
}