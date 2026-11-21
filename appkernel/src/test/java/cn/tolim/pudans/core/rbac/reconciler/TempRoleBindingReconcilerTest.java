package cn.tolim.pudans.core.rbac.reconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.TempGrants;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TempRoleBindingReconcilerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    TempGrants tempGrants;

    @Mock
    AuditLog auditLog;

    @Mock
    DomainEventPublisher domainEventPublisher;

    private TempRoleBindingReconciler reconciler;

    @BeforeEach
    void setUp() {
        reconciler = new TempRoleBindingReconciler(client, tempGrants, auditLog, domainEventPublisher);
    }

    @Test
    void expiresAnActiveBindingAndRevokesTheGrant() {
        var binding = binding(TempRoleBinding.STATUS_ACTIVE, Instant.now().minusSeconds(60));
        var user = user("alice", "uid-1");
        var auditRecord = auditRecord("audit-1");
        when(client.fetch(TempRoleBinding.class, "req-1")).thenReturn(Mono.just(binding));
        when(tempGrants.revoke(binding)).thenReturn(Mono.empty());
        when(client.fetch(User.class, "alice")).thenReturn(Mono.just(user));
        when(auditLog.newRecord(anyString(), eq("uid-1"), eq("TEMP_ROLE_EXPIRE")))
                .thenReturn(auditRecord);
        when(client.update(binding)).thenReturn(Mono.just(binding));

        StepVerifier.create(reconciler.reconcileReactive(new Reconciler.Request("req-1")))
                .assertNext(result -> assertThat(result.reEnqueue()).isFalse())
                .verifyComplete();

        assertThat(binding.getSpec().getStatus()).isEqualTo(TempRoleBinding.STATUS_EXPIRED);
        assertThat(binding.getSpec().getRevokeAuditId()).isEqualTo("audit-1");
        verify(tempGrants).revoke(binding);
        verify(auditLog).record(auditRecord);
        verify(domainEventPublisher).publish(eq("temprole.expired"), any(), isNull());
    }

    @Test
    void revokesGrantsForADeletedBinding() {
        var binding = binding(TempRoleBinding.STATUS_ACTIVE, Instant.now().plusSeconds(600));
        binding.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.fetch(TempRoleBinding.class, "req-1")).thenReturn(Mono.just(binding));
        when(tempGrants.revoke(binding)).thenReturn(Mono.empty());

        StepVerifier.create(reconciler.reconcileReactive(new Reconciler.Request("req-1")))
                .assertNext(result -> assertThat(result.reEnqueue()).isFalse())
                .verifyComplete();

        verify(tempGrants).revoke(binding);
    }

    private static TempRoleBinding binding(String status, Instant expiresAt) {
        var binding = TempRoleBinding.apply(
                "alice", List.of("viewer"), expiresAt, "reason", "ticket", "alice");
        var metadata = new Metadata();
        metadata.setName("req-1");
        binding.setMetadata(metadata);
        binding.getSpec().setStatus(status);
        return binding;
    }

    private static User user(String username, String uid) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        var spec = new User.UserSpec();
        spec.setUid(uid);
        user.setSpec(spec);
        return user;
    }

    private static AuditRecord auditRecord(String name) {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName(name);
        record.setMetadata(metadata);
        return record;
    }
}
