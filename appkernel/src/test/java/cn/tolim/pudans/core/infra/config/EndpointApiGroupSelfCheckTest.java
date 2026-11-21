package cn.tolim.pudans.core.infra.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.SchemeManager;
import cn.tolim.pudans.core.rbac.BuiltinRoleDefinitions;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@ExtendWith(MockitoExtension.class)
class EndpointApiGroupSelfCheckTest {

    @Mock
    ApplicationContext applicationContext;

    @Mock
    ObjectProvider<SchemeManager> schemeManagerProvider;

    private BuiltinRoleDefinitions builtinRoleDefinitions;

    @BeforeEach
    void setUp() {
        builtinRoleDefinitions = new BuiltinRoleDefinitions();
        when(applicationContext.getBeanProvider(SchemeManager.class)).thenReturn(schemeManagerProvider);
    }

    @Test
    void acceptsMultiSegmentPathUnderCoveredResource() {
        var router = RouterFunctions.route()
                .GET("/users/{name}/permissions", request -> ServerResponse.ok().build())
                .build();
        when(applicationContext.getBeansOfType(CustomEndpoint.class))
                .thenReturn(Map.of("endpoint", endpoint(router)));

        assertThatCode(() -> new EndpointApiGroupSelfCheck(applicationContext, builtinRoleDefinitions)
                        .run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPathUnderUnknownResource() {
        var router = RouterFunctions.route()
                .GET("/unknownresource/{name}/action", request -> ServerResponse.ok().build())
                .build();
        when(applicationContext.getBeansOfType(CustomEndpoint.class))
                .thenReturn(Map.of("endpoint", endpoint(router)));

        assertThatThrownBy(() -> new EndpointApiGroupSelfCheck(applicationContext, builtinRoleDefinitions)
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/unknownresource");
    }

    private static CustomEndpoint endpoint(RouterFunction<ServerResponse> router) {
        return new CustomEndpoint() {
            @Override
            public RouterFunction<ServerResponse> endpoint() {
                return router;
            }

            @Override
            public GroupVersion groupVersion() {
                return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
            }
        };
    }
}
