package cn.tolim.pudans.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.rbac.resource.UserGroupBinding;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.Scheme;
import cn.tolim.pudans.contract.extension.SchemeManager;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.extension.index.IndexEngine;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.r2dbc.store.ExtensionStore;
import cn.tolim.pudans.infra.r2dbc.store.ReactiveExtensionStoreClient;

@ExtendWith(MockitoExtension.class)
class ReactiveExtensionClientImplTest {

    private static final String NAME = "alice-super-admins-binding";

    private static final String STORE_NAME = "pudans.tolim.cn/usergroupbindings/" + NAME;

    @Mock
    ReactiveExtensionStoreClient storeClient;

    @Mock
    ExtensionConverter converter;

    @Mock
    SchemeManager schemeManager;

    @Mock
    IndexEngine indexEngine;

    @Mock
    ReactiveTransactionManager transactionManager;

    @Mock
    TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ReactiveExtensionClientImpl client;

    @BeforeEach
    void setUp() {
        var scheme = new Scheme(
                UserGroupBinding.class,
                new GroupVersionKind(
                        UserGroupBinding.GROUP, UserGroupBinding.VERSION, UserGroupBinding.KIND),
                "usergroupbindings",
                "usergroupbinding",
                objectMapper.createObjectNode());
        client = new ReactiveExtensionClientImpl(
                storeClient, converter, schemeManager, objectMapper, indexEngine, transactionManager);
        client.setScheduler(Schedulers.immediate());
        client.setTransactionalOperator(transactionalOperator);
        lenient().when(schemeManager.get(any(GroupVersionKind.class))).thenReturn(scheme);
        lenient()
                .when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static String bindingJson(String metadataExtra, String subject) {
        return """
                {
                  "apiVersion": "pudans.tolim.cn/v1alpha1",
                  "kind": "UserGroupBinding",
                  "metadata": {"name": "%s", "version": 1%s},
                  "subjects": [{"apiGroup": "pudans.tolim.cn", "kind": "User", "name": "%s"}],
                  "roleRef": {"apiGroup": "pudans.tolim.cn", "kind": "UserGroup", "name": "super-admins"}
                }
                """
                .formatted(NAME, metadataExtra, subject);
    }

    private static Unstructured unstructured(String json) {
        try {
            return Unstructured.OBJECT_MAPPER.readValue(json, Unstructured.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void givenStored(Unstructured stored) {
        when(storeClient.fetchByName(anyString())).thenReturn(Mono.just(new ExtensionStore(STORE_NAME, new byte[0], 1L)));
        when(converter.convertFrom(eq(Unstructured.class), any())).thenReturn(stored);
    }

    private void givenStoredThenUpdated(Unstructured stored, Unstructured updated) {
        when(storeClient.fetchByName(anyString())).thenReturn(Mono.just(new ExtensionStore(STORE_NAME, new byte[0], 1L)));
        when(converter.convertFrom(eq(Unstructured.class), any())).thenReturn(stored, updated);
    }

    @Test
    void updateRejectsAClientSuppliedDeletionTimestamp() {
        givenStored(unstructured(bindingJson("", "alice")));
        var tampered = unstructured(bindingJson(", \"deletionTimestamp\": \"2026-11-15T00:00:00Z\"", "alice"));

        StepVerifier.create(client.update(tampered))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(AccessDeniedException.class);
                    assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.FORBIDDEN);
                })
                .verify();

        verify(storeClient, never()).update(anyString(), any(), any());
    }

    @Test
    void updateRejectsClientSuppliedFinalizers() {
        givenStored(unstructured(bindingJson("", "alice")));
        var tampered = unstructured(bindingJson(", \"finalizers\": [\"evil-finalizer\"]", "alice"));

        StepVerifier.create(client.update(tampered))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AccessDeniedException.class)
                        .hasMessageContaining("finalizers"))
                .verify();

        verify(storeClient, never()).update(anyString(), any(), any());
    }

    @Test
    void updateDuringDeletionRejectsClientSuppliedFinalizers() {
        givenStored(unstructured(bindingJson(", \"deletionTimestamp\": \"2026-11-15T00:00:00Z\"", "alice")));
        var tampered = unstructured(bindingJson(
                ", \"deletionTimestamp\": \"2026-11-15T00:00:00Z\", \"finalizers\": [\"evil-finalizer\"]", "alice"));

        StepVerifier.create(client.update(tampered))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AccessDeniedException.class)
                        .hasMessageContaining("finalizers"))
                .verify();

        verify(storeClient, never()).update(anyString(), any(), any());
    }

    @Test
    void updateWithoutProtectedFieldsIsStillPersisted() {
        var stored = unstructured(bindingJson("", "alice"));
        var updated = unstructured(bindingJson("", "bob"));
        givenStoredThenUpdated(stored, updated);
        var store = new ExtensionStore(STORE_NAME, new byte[] {1}, 1L);
        when(converter.convertTo(any())).thenReturn(store);
        when(storeClient.update(eq(STORE_NAME), eq(1L), any()))
                .thenReturn(Mono.just(new ExtensionStore(STORE_NAME, new byte[] {2}, 2L)));

        StepVerifier.create(client.update(updated)).expectNextCount(1).verifyComplete();

        verify(storeClient).update(eq(STORE_NAME), eq(1L), any());
    }

    @Test
    void createRejectsReservedUserLabels() {
        var scheme = new Scheme(
                cn.tolim.pudans.contract.user.resource.User.class,
                new GroupVersionKind("pudans.tolim.cn", "v1alpha1", "User"),
                "users",
                "user",
                objectMapper.createObjectNode());
        lenient().when(schemeManager.get(any(GroupVersionKind.class))).thenReturn(scheme);
        var user = unstructured("""
                {
                  "apiVersion": "pudans.tolim.cn/v1alpha1",
                  "kind": "User",
                  "metadata": {"name": "mallory", "labels": {"pudans.tolim.cn/super-admin": "true"}},
                  "spec": {"displayName": "Mallory"}
                }
                """);

        StepVerifier.create(client.create(user))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(AccessDeniedException.class))
                .verify();

        verify(storeClient, never()).create(anyString(), any());
    }

    @Test
    void updateFailsWhenTheStoredObjectDisappeared() {
        when(storeClient.fetchByName(anyString())).thenReturn(Mono.empty());
        var missing = unstructured(bindingJson("", "alice"));

        StepVerifier.create(client.update(missing))
                .expectError(cn.tolim.pudans.extension.exception.ExtensionNotFoundException.class)
                .verify();
    }
}
