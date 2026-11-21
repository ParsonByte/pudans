package cn.tolim.pudans.core.ai.harness.skill.curator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiSkill;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SkillPromotionTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    SkillPromotionGate gate;

    @Test
    void promotesASkillAndPersistsUsageAndAudit() {
        var usage = new InMemorySkillUsageStore();
        var audit = new InMemorySkillAuditLog();
        var skill = skill("release", AiSkill.Status.DRAFT, false);
        when(client.get(AiSkill.class, "release")).thenReturn(Mono.just(skill));
        when(client.update(skill)).thenReturn(Mono.just(skill));
        var service = new SkillPromotion(client, usage, audit, gate);

        StepVerifier.create(service.promote("release", "alice"))
                .assertNext(result -> {
                    assertThat(result.status()).isEqualTo(SkillPromotion.Status.APPROVED);
                    assertThat(skill.getSpec().isEnabled()).isTrue();
                    assertThat(skill.getSpec().getStatus()).isEqualTo(AiSkill.Status.PUBLISHED);
                })
                .verifyComplete();

        assertThat(usage.get("release")).hasValueSatisfying(record ->
                assertThat(record.promotedBy()).isEqualTo("alice"));
        assertThat(audit.entries()).extracting(SkillAuditLog.Entry::decision).containsExactly("approved");
    }

    @Test
    void archivesASkillAndMarksUsageArchived() {
        var usage = new InMemorySkillUsageStore();
        var audit = new InMemorySkillAuditLog();
        var skill = skill("release", AiSkill.Status.PUBLISHED, true);
        when(client.get(AiSkill.class, "release")).thenReturn(Mono.just(skill));
        when(client.update(skill)).thenReturn(Mono.just(skill));
        var service = new SkillPromotion(client, usage, audit, gate);

        StepVerifier.create(service.archive("release", "alice"))
                .assertNext(result -> assertThat(result.status()).isEqualTo(SkillPromotion.Status.ARCHIVED))
                .verifyComplete();

        assertThat(skill.getSpec().getStatus()).isEqualTo(AiSkill.Status.ARCHIVED);
        assertThat(usage.get("release"))
                .hasValueSatisfying(record -> assertThat(record.state()).isEqualTo(SkillUsageRecord.State.ARCHIVED));
        assertThat(audit.entries()).extracting(SkillAuditLog.Entry::operation).containsExactly("archive");
    }

    @Test
    void autoPromotesDraftSkillsApprovedByTheLearningGate() {
        var usage = new InMemorySkillUsageStore();
        var audit = new InMemorySkillAuditLog();
        var draft = skill("release", AiSkill.Status.DRAFT, false);
        when(client.listAll(eq(AiSkill.class), any(), any())).thenReturn(Flux.just(draft));
        when(gate.review(any())).thenReturn(Mono.just(
                new SkillPromotionGate.Decision.Approve("curator", List.of("production"))));
        when(client.update(draft)).thenReturn(Mono.just(draft));
        var service = new SkillPromotion(client, usage, audit, gate);

        StepVerifier.create(service.autoPromote())
                .assertNext(count -> assertThat(count).isEqualTo(1L))
                .verifyComplete();

        assertThat(draft.getSpec().getStatus()).isEqualTo(AiSkill.Status.PUBLISHED);
        assertThat(usage.get("release"))
                .hasValueSatisfying(record -> assertThat(record.environments()).containsExactly("production"));
    }

    private static AiSkill skill(String name, AiSkill.Status status, boolean enabled) {
        var skill = new AiSkill();
        var metadata = new Metadata();
        metadata.setName(name);
        skill.setMetadata(metadata);
        skill.getSpec().setDisplayName(name);
        skill.getSpec().setStatus(status);
        skill.getSpec().setEnabled(enabled);
        skill.getSpec().setContent("# " + name);
        return skill;
    }
}