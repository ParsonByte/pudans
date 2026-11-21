package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KnowledgeBaseAclTest {

    private final KnowledgeBaseAcl acl = new KnowledgeBaseAcl();

    @Test
    void disabledKnowledgeBasesRemainVisibleButUnreadable() {
        var knowledgeBase = knowledgeBase(KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        knowledgeBase.getSpec().setStatus(KnowledgeBase.BaseStatus.DISABLED);

        assertThat(acl.canRead(knowledgeBase, "alice", Set.of(), "dept-b")).isFalse();
        assertThat(acl.canView(knowledgeBase, "alice", Set.of(), "dept-b")).isTrue();
    }

    @Test
    void allowsEveryUserToReadACompanyKnowledgeBase() {
        var knowledgeBase = knowledgeBase(KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");

        assertThat(acl.canRead(knowledgeBase, "alice", Set.of(), "dept-b")).isTrue();
    }

    @Test
    void restrictsPrivateKnowledgeBaseToItsOwner() {
        var knowledgeBase = knowledgeBase(KnowledgeBase.Visibility.PRIVATE, "alice", null);

        assertThat(acl.canRead(knowledgeBase, "alice", Set.of(), "dept-b")).isTrue();
        assertThat(acl.canRead(knowledgeBase, "bob", Set.of(), "dept-b")).isFalse();
    }

    @Test
    void restrictsTeamKnowledgeBaseToItsDepartment() {
        var knowledgeBase = knowledgeBase(KnowledgeBase.Visibility.TEAM, "owner", "dept-a");

        assertThat(acl.canRead(knowledgeBase, "bob", Set.of(), "dept-a")).isTrue();
        assertThat(acl.canRead(knowledgeBase, "bob", Set.of(), "dept-b")).isFalse();
    }

    @Test
    void grantsAccessThroughUserAclBesidesVisibility() {
        var knowledgeBase = knowledgeBase(
                KnowledgeBase.Visibility.PRIVATE,
                "bob",
                null,
                entry(KnowledgeBase.SubjectType.USER, "alice", KnowledgeBase.Permission.READ));

        assertThat(acl.canRead(knowledgeBase, "alice", Set.of(), null)).isTrue();
        assertThat(acl.canRead(knowledgeBase, "carol", Set.of(), null)).isFalse();
    }

    @Test
    void grantsAccessThroughRoleAcl() {
        var knowledgeBase = knowledgeBase(
                KnowledgeBase.Visibility.TEAM,
                "owner",
                "dept-a",
                entry(KnowledgeBase.SubjectType.ROLE, "ai-user", KnowledgeBase.Permission.READ));

        assertThat(acl.canRead(knowledgeBase, "bob", Set.of("ai-user"), "dept-b")).isTrue();
        assertThat(acl.canRead(knowledgeBase, "bob", Set.of("other"), "dept-b")).isFalse();
    }

    @Test
    void grantsAccessThroughDepartmentAcl() {
        var knowledgeBase = knowledgeBase(
                KnowledgeBase.Visibility.TEAM,
                "owner",
                "dept-a",
                entry(KnowledgeBase.SubjectType.DEPT, "dept-b", KnowledgeBase.Permission.READ));

        assertThat(acl.canRead(knowledgeBase, "bob", Set.of(), "dept-b")).isTrue();
    }

    @Test
    void treatsWriteAndAdminAclPermissionsAsReadable() {
        var write = knowledgeBase(
                KnowledgeBase.Visibility.PRIVATE,
                "owner",
                null,
                entry(KnowledgeBase.SubjectType.USER, "alice", KnowledgeBase.Permission.WRITE));
        var admin = knowledgeBase(
                KnowledgeBase.Visibility.PRIVATE,
                "owner",
                null,
                entry(KnowledgeBase.SubjectType.USER, "alice", KnowledgeBase.Permission.ADMIN));

        assertThat(acl.canRead(write, "alice", Set.of(), null)).isTrue();
        assertThat(acl.canRead(admin, "alice", Set.of(), null)).isTrue();
    }

    @Test
    void rejectsDisabledKnowledgeBaseEvenForACompanyVisibility() {
        var knowledgeBase = knowledgeBase(KnowledgeBase.Visibility.COMPANY, "owner", "dept-a");
        knowledgeBase.getSpec().setStatus(KnowledgeBase.BaseStatus.DISABLED);

        assertThat(acl.canRead(knowledgeBase, "alice", Set.of(), "dept-a")).isFalse();
    }

    @Test
    void doesNotMatchMissingOrBlankSubjects() {
        var knowledgeBase = knowledgeBase(
                KnowledgeBase.Visibility.PRIVATE,
                "owner",
                null,
                entry(KnowledgeBase.SubjectType.USER, "", KnowledgeBase.Permission.READ));

        assertThat(acl.canRead(knowledgeBase, null, null, null)).isFalse();
        assertThat(acl.canRead(null, "alice", Set.of(), "dept-a")).isFalse();
    }

    private static KnowledgeBase knowledgeBase(
            KnowledgeBase.Visibility visibility, String ownerUserId, String ownerDeptId, KnowledgeBase.Acl... acl) {
        var knowledgeBase = new KnowledgeBase();
        knowledgeBase.getSpec().setVisibility(visibility);
        knowledgeBase.getSpec().setOwnerUserId(ownerUserId);
        knowledgeBase.getSpec().setOwnerDeptId(ownerDeptId);
        for (var entry : acl) {
            knowledgeBase.getSpec().getAcl().add(entry);
        }
        return knowledgeBase;
    }

    private static KnowledgeBase.Acl entry(
            KnowledgeBase.SubjectType subjectType, String subjectId, KnowledgeBase.Permission permission) {
        var acl = new KnowledgeBase.Acl();
        acl.setSubjectType(subjectType);
        acl.setSubjectId(subjectId);
        acl.setPermission(permission);
        return acl;
    }
}
