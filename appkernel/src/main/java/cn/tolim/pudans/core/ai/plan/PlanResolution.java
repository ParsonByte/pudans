package cn.tolim.pudans.core.ai.plan;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.contract.ai.resource.AiPlanBinding;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.PudansTime;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanResolution {

    public static final String CODE_PLAN_MISSING = "ai-plan-missing";

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private static final long CACHE_SIZE = 10_000;

    private final Accounts accounts;

    private final Groups userGroupService;

    private final ReactiveExtensionClient client;

    private final AiSettings settingService;

    private final PlanCatalog catalogService;

    private final Cache<String, ResolvedPlan> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(CACHE_TTL)
            .maximumSize(CACHE_SIZE)
            .build();

    private Clock clock = PudansTime.systemClock();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<ResolvedPlan> resolve(String uid) {
        Assert.hasText(uid, "uid 不能为空");
        var cached = cache.getIfPresent(uid);
        if (cached != null) {
            return Mono.just(cached);
        }
        return load(uid).doOnNext(resolved -> cache.put(uid, resolved));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private Mono<ResolvedPlan> load(String uid) {
        var now = clock.instant();
        return billing()
                .flatMap(billing -> accounts
                        .getUserByUid(uid)
                        .flatMapMany(user -> groupNamesOf(
                                user.getMetadata() == null ? null : user.getMetadata().getName()))
                        .collectList()
                        .flatMap(groups -> bindingsFor(uid, groups)
                                .flatMap(bindings -> resolveBinding(uid, groups, bindings, now)))
                        .switchIfEmpty(Mono.defer(() -> fromDefault(billing))));
    }

    private Mono<AiSetting.Billing> billing() {
        return settingService
                .spec()
                .map(spec -> spec.getBilling() == null ? new AiSetting.Billing() : spec.getBilling())
                .defaultIfEmpty(new AiSetting.Billing());
    }

    private Flux<String> groupNamesOf(String username) {
        if (!StringUtils.hasText(username)) {
            return Flux.empty();
        }
        return userGroupService
                .listMembershipBindings(username)
                .map(binding -> binding.getRoleRef() == null ? null : binding.getRoleRef().getName())
                .filter(StringUtils::hasText)
                .distinct();
    }

    private Mono<List<AiPlanBinding>> bindingsFor(String uid, List<String> groups) {
        var names = new LinkedHashSet<String>();
        names.add(uid);
        names.addAll(groups);
        var options = ListOptions.builder().andQuery(in("spec.subject.name", names)).build();
        return client.listAll(AiPlanBinding.class, options, defaultSort()).collectList();
    }

    private Mono<ResolvedPlan> resolveBinding(
            String uid, List<String> groups, List<AiPlanBinding> bindings, Instant now) {
        var direct = bindings.stream()
                .filter(binding -> matches(binding, AiPlanBinding.SubjectKind.User, uid, now))
                .sorted(Comparator.comparing((AiPlanBinding binding) -> binding.getMetadata().getName()))
                .toList();
        var grouped = bindings.stream()
                .filter(binding -> matchesGroup(binding, groups, now))
                .sorted(Comparator.comparingInt((AiPlanBinding binding) -> binding.getSpec().getPriority())
                        .reversed()
                        .thenComparing(binding -> binding.getMetadata().getName()))
                .toList();
        warnOnGroupTie(grouped);
        var ordered = new ArrayList<AiPlanBinding>(direct);
        ordered.addAll(grouped);
        return Flux.fromIterable(ordered)
                .concatMap(binding -> catalogService
                        .find(binding.getSpec().getPlan())
                        .filter(catalogService::usable)
                        .map(plan -> resolved(plan, matchedBy(uid, binding))))
                .next();
    }

    private Mono<ResolvedPlan> fromDefault(AiSetting.Billing billing) {
        if (billing.getUnassignedPolicy() != AiSetting.UnassignedPolicy.DEFAULT_PLAN
                || !StringUtils.hasText(billing.getDefaultPlan())) {
            return missing();
        }
        return catalogService
                .find(billing.getDefaultPlan())
                .filter(catalogService::usable)
                .map(plan -> resolved(plan, "DefaultPlan"))
                .switchIfEmpty(Mono.defer(PlanResolution::missing));
    }

    private static boolean matchesGroup(
            AiPlanBinding binding, List<String> groups, Instant now) {
        if (groups.isEmpty() || !bound(binding, now)) {
            return false;
        }
        var subject = binding.getSpec().getSubject();
        return subject != null
                && subject.getKind() == AiPlanBinding.SubjectKind.UserGroup
                && groups.contains(subject.getName());
    }

    private static boolean matches(
            AiPlanBinding binding, AiPlanBinding.SubjectKind kind, String name, Instant now) {
        if (!bound(binding, now)) {
            return false;
        }
        var subject = binding.getSpec().getSubject();
        return subject != null && subject.getKind() == kind && name.equals(subject.getName());
    }

    private static boolean bound(AiPlanBinding binding, Instant now) {
        if (binding == null || binding.getSpec() == null || binding.getMetadata() == null) {
            return false;
        }
        var from = binding.getSpec().getEffectiveFrom();
        var to = binding.getSpec().getEffectiveTo();
        return (from == null || !from.isAfter(now)) && (to == null || to.isAfter(now));
    }

    private static String matchedBy(String uid, AiPlanBinding binding) {
        var subject = binding.getSpec().getSubject();
        return subject.getKind() == AiPlanBinding.SubjectKind.User
                ? "User/" + uid
                : "UserGroup/" + subject.getName();
    }

    private void warnOnGroupTie(List<AiPlanBinding> grouped) {
        if (grouped.size() < 2) {
            return;
        }
        var topPriority = grouped.get(0).getSpec().getPriority();
        var tied = grouped.stream()
                .filter(binding -> binding.getSpec().getPriority() == topPriority)
                .count();
        if (tied > 1) {
            log.warn(
                    "用户组套餐绑定存在相同优先级 {}，按绑定名称选择 {}。",
                    topPriority,
                    grouped.get(0).getMetadata().getName());
        }
    }

    private static ResolvedPlan resolved(AiPlan plan, String matchedBy) {
        var name = plan.getMetadata() == null ? null : plan.getMetadata().getName();
        return new ResolvedPlan(name, plan.getSpec().getDisplayName(), matchedBy, plan);
    }

    private static Mono<ResolvedPlan> missing() {
        return Mono.error(new ApiException(
                HttpStatus.FORBIDDEN, CODE_PLAN_MISSING, "当前用户未分配 AI 套餐。"));
    }

    public record ResolvedPlan(
            @Nullable String planName, String displayName, String matchedBy, AiPlan plan) {
    }
}