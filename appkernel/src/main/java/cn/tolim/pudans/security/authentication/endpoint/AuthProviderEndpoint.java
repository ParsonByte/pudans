package cn.tolim.pudans.security.authentication.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.security.authentication.AuthProviderService;
import cn.tolim.pudans.security.authentication.ListedAuthProvider;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuthProviderEndpoint implements CustomEndpoint {

    private final AuthProviderService authProviderService;

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "AuthV1alpha1Auth";
        return SpringdocRouteBuilder.route()
                .GET(
                        "/auth-providers",
                        this::listAuthProviders,
                        builder -> builder.operationId("listAuthProviders")
                                .description("Lists all auth providers")
                                .tag(tag)
                                .response(responseBuilder().implementationArray(ListedAuthProvider.class)))
                .PUT(
                        "/auth-providers/{name}/enable",
                        this::enableAuthProvider,
                        builder -> builder.operationId("enableAuthProvider")
                                .description("Enables an auth provider")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .description("metadata.name of the auth provider to enable.")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(AuthProvider.class)))
                .PUT(
                        "/auth-providers/{name}/disable",
                        this::disableAuthProvider,
                        builder -> builder.operationId("disableAuthProvider")
                                .description("Disables an auth provider")
                                .tag(tag)
                                .parameter(parameterBuilder()
                                        .name("name")
                                        .description("metadata.name of the auth provider to disable.")
                                        .in(ParameterIn.PATH)
                                        .required(true)
                                        .implementation(String.class))
                                .response(responseBuilder().implementation(AuthProvider.class)))
                .build();
    }

    private Mono<ServerResponse> enableAuthProvider(ServerRequest request) {
        String name = request.pathVariable("name");
        return authProviderService
                .enable(name)
                .flatMap(authProvider -> ServerResponse.ok().bodyValue(authProvider));
    }

    private Mono<ServerResponse> disableAuthProvider(ServerRequest request) {
        String name = request.pathVariable("name");
        return authProviderService
                .disable(name)
                .flatMap(authProvider -> ServerResponse.ok().bodyValue(authProvider));
    }

    Mono<ServerResponse> listAuthProviders(ServerRequest request) {
        return authProviderService
                .listAll()
                .flatMap(providers -> ServerResponse.ok().bodyValue(providers));
    }
}