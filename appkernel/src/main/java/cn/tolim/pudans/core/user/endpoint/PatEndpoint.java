package cn.tolim.pudans.core.user.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.core.user.service.UserScopedPatHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
class PatEndpoint implements CustomEndpoint {

    private final UserScopedPatHandler patHandler;

    public PatEndpoint(UserScopedPatHandler patHandler) {
        this.patHandler = patHandler;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = PersonalAccessToken.KIND + "V1alpha1";
        return route().nest(
                        path("/personalaccesstokens"),
                        () -> route().POST(
                                        patHandler::create,
                                        builder -> builder.tag(tag)
                                                .operationId("GeneratePat")
                                                .description("Generate a PAT.")
                                                .requestBody(requestBodyBuilder()
                                                        .required(true)
                                                        .implementation(PersonalAccessToken.class))
                                                .response(responseBuilder().implementation(PersonalAccessToken.class)))
                                .GET(
                                        patHandler::list,
                                        builder -> builder.tag(tag)
                                                .operationId("ObtainPats")
                                                .description("Obtain PAT list.")
                                                .response(responseBuilder()
                                                        .implementationArray(PersonalAccessToken.class)))
                                .GET(
                                        "/{name}",
                                        patHandler::get,
                                        builder -> builder.tag(tag)
                                                .operationId("ObtainPat")
                                                .description("Obtain a PAT.")
                                                .parameter(parameterBuilder()
                                                        .in(ParameterIn.PATH)
                                                        .required(true)
                                                        .description("metadata.name of the PAT to fetch.")
                                                        .name("name"))
                                                .response(responseBuilder().implementation(PersonalAccessToken.class)))
                                .PUT(
                                        "/{name}/actions/revocation",
                                        patHandler::revoke,
                                        builder -> builder.tag(tag)
                                                .operationId("RevokePat")
                                                .description("Revoke a PAT")
                                                .parameter(parameterBuilder()
                                                        .in(ParameterIn.PATH)
                                                        .required(true)
                                                        .description("metadata.name of the PAT to revoke.")
                                                        .name("name"))
                                                .response(responseBuilder().implementation(PersonalAccessToken.class)))
                                .PUT(
                                        "/{name}/actions/restoration",
                                        patHandler::restore,
                                        builder -> builder.tag(tag)
                                                .operationId("RestorePat")
                                                .description("Restore a PAT.")
                                                .parameter(parameterBuilder()
                                                        .in(ParameterIn.PATH)
                                                        .required(true)
                                                        .description("metadata.name of the PAT to restore.")
                                                        .name("name"))
                                                .response(responseBuilder().implementation(PersonalAccessToken.class)))
                                .DELETE(
                                        "/{name}",
                                        patHandler::delete,
                                        builder -> builder.tag(tag)
                                                .operationId("DeletePat")
                                                .description("Delete a PAT")
                                                .parameter(parameterBuilder()
                                                        .in(ParameterIn.PATH)
                                                        .required(true)
                                                        .description("metadata.name of the PAT to delete.")
                                                        .name("name"))
                                                .response(responseBuilder().implementation(PersonalAccessToken.class)))
                                .build())
                .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}