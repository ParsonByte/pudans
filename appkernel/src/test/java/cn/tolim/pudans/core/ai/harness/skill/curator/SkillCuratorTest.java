package cn.tolim.pudans.core.ai.harness.skill.curator;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.skill.WorkspaceSkillRepository;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceManager;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

class SkillCuratorTest {

    @TempDir
    Path tempDir;

    @Test
    void approvesDraftAndRecordsUsageAndAudit() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        var drafts = WorkspaceSkillRepository.drafts(workspace);
        var published = new WorkspaceSkillRepository(workspace);
        writeDraft(workspace, "gamma", "Gamma skill");
        var usage = new InMemorySkillUsageStore();
        var audit = new InMemorySkillAuditLog();
        var reviewed = new AtomicReference<SkillCandidate>();
        SkillPromotionGate gate = candidate -> {
            reviewed.set(candidate);
            return Mono.just(new SkillPromotionGate.Decision.Approve("reviewer-1", List.of("prod")));
        };
        var curator = new SkillCurator(drafts, published, usage, audit, gate);

        var result = curator.promote("gamma").block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SkillCurator.Status.APPROVED);
        assertThat(reviewed.get().name()).isEqualTo("gamma");
        assertThat(reviewed.get().supportFiles()).containsExactly("references/notes.md");
        var promoted = published.find("gamma").orElseThrow();
        assertThat(promoted.description()).isEqualTo("Gamma skill");
        assertThat(promoted.resources().read("references/notes.md")).contains("notes");
        assertThat(drafts.find("gamma")).isEmpty();
        var usageRecord = usage.get("gamma").orElseThrow();
        assertThat(usageRecord.createdBy()).isEqualTo("agent");
        assertThat(usageRecord.state()).isEqualTo(SkillUsageRecord.State.ACTIVE);
        assertThat(usageRecord.promotedBy()).isEqualTo("reviewer-1");
        assertThat(usageRecord.environments()).containsExactly("prod");
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.operation()).isEqualTo("promote");
            assertThat(entry.target()).isEqualTo("gamma");
            assertThat(entry.decision()).isEqualTo("approved");
            assertThat(entry.actor()).isEqualTo("reviewer-1");
        });
    }

    @Test
    void rejectsDraftWithoutPublishing() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        var drafts = WorkspaceSkillRepository.drafts(workspace);
        var published = new WorkspaceSkillRepository(workspace);
        writeDraft(workspace, "delta", "Delta skill");
        var audit = new InMemorySkillAuditLog();
        SkillPromotionGate gate =
                candidate -> Mono.just(new SkillPromotionGate.Decision.Reject("reviewer-2", "unsafe"));
        var curator = new SkillCurator(drafts, published, new InMemorySkillUsageStore(), audit, gate);

        var result = curator.promote("delta").block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SkillCurator.Status.REJECTED);
        assertThat(published.find("delta")).isEmpty();
        assertThat(drafts.find("delta")).isPresent();
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.decision()).isEqualTo("rejected");
            assertThat(entry.actor()).isEqualTo("reviewer-2");
            assertThat(entry.detail()).isEqualTo("unsafe");
        });
    }

    @Test
    void reportsInvalidWhenTheDraftDoesNotExist() {
        var workspace = workspace(tempDir.resolve("workspace"));
        var audit = new InMemorySkillAuditLog();
        var curator = new SkillCurator(
                WorkspaceSkillRepository.drafts(workspace),
                new WorkspaceSkillRepository(workspace),
                new InMemorySkillUsageStore(),
                audit,
                candidate -> Mono.just(new SkillPromotionGate.Decision.Approve("reviewer-1", List.of("prod"))));

        var result = curator.promote("missing").block();

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(SkillCurator.Status.INVALID);
        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.target()).isEqualTo("missing");
            assertThat(entry.decision()).isEqualTo("invalid");
        });
    }

    @Test
    void archivesPublishedSkillAndUpdatesLifecycleState() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        var drafts = WorkspaceSkillRepository.drafts(workspace);
        var published = new WorkspaceSkillRepository(workspace);
        writeDraft(workspace, "gamma", "Gamma skill");
        var usage = new InMemorySkillUsageStore();
        var audit = new InMemorySkillAuditLog();
        var curator = new SkillCurator(
                drafts,
                published,
                usage,
                audit,
                candidate -> Mono.just(new SkillPromotionGate.Decision.Approve("reviewer-1", List.of("prod"))));
        assertThat(curator.promote("gamma").block().status()).isEqualTo(SkillCurator.Status.APPROVED);

        var result = curator.archive("gamma", "ops");

        assertThat(result.status()).isEqualTo(SkillCurator.Status.ARCHIVED);
        assertThat(published.find("gamma")).isEmpty();
        assertThat(usage.get("gamma").orElseThrow().state()).isEqualTo(SkillUsageRecord.State.ARCHIVED);
        assertThat(audit.entries()).extracting(SkillAuditLog.Entry::operation).containsExactly("promote", "archive");
    }

    @Test
    void tracksUsageCounters() {
        var usage = new InMemorySkillUsageStore();

        usage.recordView("alpha");
        usage.recordUse("alpha");
        usage.recordPatch("alpha");

        var record = usage.get("alpha").orElseThrow();
        assertThat(record.viewCount()).isEqualTo(1);
        assertThat(record.useCount()).isEqualTo(1);
        assertThat(record.patchCount()).isEqualTo(1);
        assertThat(record.lastActivityAt()).isNotNull();
        assertThat(usage.all()).hasSize(1);
    }

    private static WorkspaceManager workspace(Path root) {
        var manager = new WorkspaceManager(WorkspaceSpec.defaults(root));
        manager.ensureDirectories();
        return manager;
    }

    private static void writeDraft(WorkspaceManager workspace, String name, String description)
            throws IOException {
        var skillDir = workspace.resolve("skills/_drafts").resolve(name);
        Files.createDirectories(skillDir.resolve("references"));
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + description + "\n---\n# " + name + "\n");
        Files.writeString(skillDir.resolve("references/notes.md"), "notes");
    }
}