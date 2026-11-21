package cn.tolim.pudans.extension.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.GroupVersionKind;
import cn.tolim.pudans.extension.api.JsonExtension;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.Scheme;
import cn.tolim.pudans.core.rbac.BuiltinEntityPolicy;
import cn.tolim.pudans.infra.exception.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ExtensionPatchHandlerTest {

    private static final String NAME = "alice-super-admins-binding";

    private static final MediaType JSON_PATCH = MediaType.valueOf("application/json-patch+json");

    @Mock
    ReactiveExtensionClient client;

    @Mock
    BuiltinEntityPolicy builtinEntityPolicy;

    @Mock
    ServerRequest request;

    @Mock
    ServerRequest.Headers headers;

    private final ObjectMapper mapper = new ObjectMapper();

    private ExtensionPatchHandler handler;

    private JsonExtension current;

    @BeforeEach
    void setUp() {
        var scheme = new Scheme(
                UserGroupBinding.class,
                new GroupVersionKind(
                        UserGroupBinding.GROUP, UserGroupBinding.VERSION, UserGroupBinding.KIND),
                "usergroupbindings",
                "usergroupbinding",
                mapper.createObjectNode());
        handler = new ExtensionPatchHandler(scheme, client, builtinEntityPolicy);
        current = new JsonExtension(mapper, bindingNode());
        org.mockito.Mockito.lenient().when(client.update(any())).thenReturn(Mono.empty());
    }

    private ObjectNode bindingNode() {
        try {
            return (ObjectNode) mapper.readTree("""
                    {
                      "apiVersion": "pudans.tolim.cn/v1alpha1",
                      "kind": "UserGroupBinding",
                      "metadata": {"name": "%s", "version": 1},
                      "subjects": [{"apiGroup": "pudans.tolim.cn", "kind": "User", "name": "alice"}],
                      "roleRef": {"apiGroup": "pudans.tolim.cn", "kind": "UserGroup", "name": "super-admins"}
                    }
                    """
                    .formatted(NAME));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonPatch patchReplacingRoleRef() {
        try {
            return JsonPatch.fromJson(mapper.readTree(
                    "[{\"op\":\"replace\",\"path\":\"/roleRef/name\",\"value\":\"normal-users\"}]"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void givenPatchRequest(JsonPatch patch) {
        when(request.pathVariable("name")).thenReturn(NAME);
        when(request.headers()).thenReturn(headers);
        when(headers.contentType()).thenReturn(Optional.of(JSON_PATCH));
        when(request.bodyToMono(JsonPatch.class)).thenReturn(Mono.just(patch));
        when(client.getJsonExtension(any(GroupVersionKind.class), eq(NAME))).thenReturn(Mono.just(current));
    }

    @Test
    void patchResultIsValidatedBeforeItIsPersisted() throws Exception {
        givenPatchRequest(patchReplacingRoleRef());
        when(builtinEntityPolicy.checkUpdate(any(GroupVersionKind.class), eq(NAME), any(Extension.class)))
                .thenReturn(Mono.error(new AccessDeniedException("super administrator binding is protected")));

        StepVerifier.create(handler.handle(request))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AccessDeniedException.class))
                .verify();

        verify(client, never()).update(any());
    }

    @Test
    void policySeesThePatchedObjectAndNotTheRequestName() {
        givenPatchRequest(patchReplacingRoleRef());
        when(builtinEntityPolicy.checkUpdate(any(GroupVersionKind.class), eq(NAME), any(Extension.class)))
                .thenReturn(Mono.empty());
        when(client.update(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(handler.handle(request)).expectNextCount(1).verifyComplete();

        var captor = ArgumentCaptor.forClass(Extension.class);
        verify(builtinEntityPolicy).checkUpdate(any(GroupVersionKind.class), eq(NAME), captor.capture());
        var patched = (JsonExtension) captor.getValue();
        assertThat(patched.getInternal().path("roleRef").path("name").asText())
                .isEqualTo("normal-users");
    }

    @Test
    void patchThatRenamesTheObjectIsRejectedBeforeThePolicyRuns() throws Exception {
        when(request.pathVariable("name")).thenReturn(NAME);
        when(request.headers()).thenReturn(headers);
        when(headers.contentType()).thenReturn(Optional.of(JSON_PATCH));
        when(request.bodyToMono(JsonPatch.class))
                .thenReturn(Mono.just(JsonPatch.fromJson(mapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/metadata/name\",\"value\":\"other-binding\"}]"))));
        when(client.getJsonExtension(any(GroupVersionKind.class), eq(NAME))).thenReturn(Mono.just(current));

        StepVerifier.create(handler.handle(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(org.springframework.web.server.ServerWebInputException.class))
                .verify();

        verify(builtinEntityPolicy, never()).checkUpdate(any(), anyString(), any());
    }
}
