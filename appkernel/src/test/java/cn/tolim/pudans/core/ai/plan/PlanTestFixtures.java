package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiPlanBinding;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.contract.rbac.UserGroupBinding;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.UsageLedgerRepository.LedgerRow;
import java.time.Instant;

final class PlanTestFixtures {

    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private PlanTestFixtures() {}

    static AiPlan plan(String name) {
        return plan(name, 3_000_000L, 20_000_000L);
    }

    static AiPlan plan(String name, long fiveHour, long weekly) {
        var plan = new AiPlan();
        var metadata = new Metadata();
        metadata.setName(name);
        plan.setMetadata(metadata);
        plan.getSpec().setDisplayName("套餐 " + name);
        plan.getSpec().getQuota().getFiveHour().setLimit(fiveHour);
        plan.getSpec().getQuota().getWeekly().setLimit(weekly);
        return plan;
    }

    static AiPlanBinding binding(
            String name,
            AiPlanBinding.SubjectKind kind,
            String subject,
            String plan,
            int priority) {
        var binding = new AiPlanBinding();
        var metadata = new Metadata();
        metadata.setName(name);
        binding.setMetadata(metadata);
        binding.getSpec().setPlan(plan);
        binding.getSpec().setPriority(priority);
        var bindingSubject = new AiPlanBinding.Subject();
        bindingSubject.setKind(kind);
        bindingSubject.setName(subject);
        binding.getSpec().setSubject(bindingSubject);
        return binding;
    }

    static UserGroupBinding membership(String groupName) {
        var binding = new UserGroupBinding();
        binding.setRoleRef(UserGroupBinding.RoleRef.userGroup(groupName));
        return binding;
    }

    static AiSetting.Spec setting(String defaultPlan, AiSetting.UnassignedPolicy policy) {
        var spec = new AiSetting.Spec();
        spec.getBilling().setDefaultPlan(defaultPlan);
        spec.getBilling().setUnassignedPolicy(policy);
        return spec;
    }

    static PlanCounterRow counter(
            PlanWindowKind kind,
            String windowKey,
            Instant startsAt,
            Instant endsAt,
            long limit,
            long used,
            long reserved) {
        return new PlanCounterRow(
                "uid-alice",
                kind.name(),
                windowKey,
                startsAt,
                endsAt,
                limit,
                used,
                reserved,
                PlanWindowClock.ANCHOR_PLAN,
                0);
    }

    static PlanWindow activeFiveHour(long used, long reserved) {
        var startsAt = NOW.minusSeconds(3600);
        var endsAt = NOW.plusSeconds(4 * 3600);
        return new PlanWindow(PlanWindowClock.fiveHourKey(startsAt), startsAt, endsAt);
    }

    static PlanWindow activeWeek() {
        var startsAt = Instant.parse("2026-11-15T16:00:00Z");
        return new PlanWindow(PlanWindowClock.weekKey(startsAt), startsAt, startsAt.plusSeconds(7 * 24 * 3600));
    }

    static LedgerRow ledgerRow(String requestId, String userId, String status, long units) {
        return new LedgerRow(
                1L,
                requestId,
                userId,
                null,
                "standard",
                "USER",
                "CHAT",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                units,
                units,
                status,
                null,
                NOW,
                null);
    }
}