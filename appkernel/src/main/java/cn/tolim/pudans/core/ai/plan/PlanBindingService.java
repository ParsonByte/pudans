package cn.tolim.pudans.core.ai.plan;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;

import cn.tolim.pudans.contract.core.extension.ai.AiPlanBinding;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlanBindingService {

    public static final String CODE_BINDING_INVALID = "ai-plan-binding-invalid";

    public static final String CODE_BINDING_CONFLICT = "ai-plan-binding-conflict";

    private final ReactiveExtensionClient client;

    private final PlanCatalogService catalogService;

    private final UserGroupService userGroupService;

    private final UserService userService;

    private final PlanResolutionService resolutionService;

    private Clock clock = PudansTime.systemClock();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<BulkBindResult> bulk(BulkBindRequest request) {
        if (request == null
                || !StringUtils.hasText(request.plan())
                || request.subjects() == null
                || request.subjects().isEmpty()) {
            return Mono.error(invalid("批量绑定请求不能为空。"));
        }
        if (request.effectiveFrom() != null
                && request.effectiveTo() != null
                && !request.effectiveFrom().isBefore(request.effectiveTo())) {
            return Mono.error(invalid("生效开始时间必须早于结束时间。"));
        }
        var subjects = new LinkedHashSet<>(request.subjects());
        return catalogService
                .requireEnabled(request.plan())
                .flatMap(plan -> validateSubjects(subjects)
                        .then(Mono.defer(() -> Flux.fromIterable(subjects)
                                .concatMap(subject -> apply(
                                        request.plan(),
                                        subject,
                                        request.priority(),
                                        request.replaceExisting(),
                                        request.effectiveFrom(),
                                        request.effectiveTo()))
                                .collectList())))
                .map(bindings -> new BulkBindResult(request.plan(), bindings))
                .doOnNext(result -> resolutionService.invalidateAll());
    }

    private Mono<Void> validateSubjects(LinkedHashSet<BindingSubject> subjects) {
        return Flux.fromIterable(subjects)
                .concatMap(subject -> {
                    if (subject == null || subject.kind() == null || !StringUtils.hasText(subject.name())) {
                        return Mono.error(invalid("绑定主体必须包含有效的类型与名称。"));
                    }
                    if (subject.kind() == AiPlanBinding.SubjectKind.UserGroup) {
                        return userGroupService
                                .getUserGroup(subject.name())
                                .switchIfEmpty(Mono.error(invalid("用户组 [" + subject.name() + "] 不存在。")))
                                .then();
                    }
                    return userService
                            .getUserByUid(subject.name())
                            .switchIfEmpty(Mono.error(invalid("用户 [" + subject.name() + "] 不存在。")))
                            .then();
                })
                .then();
    }

    private Mono<BindingSummary> apply(
            String planName,
            BindingSubject subject,
            int priority,
            boolean replaceExisting,
            @Nullable Instant effectiveFrom,
            @Nullable Instant effectiveTo) {
        var now = clock.instant();
        return effectiveBindings(subject, now)
                .collectList()
                .flatMap(existing -> {
                    if (existing.isEmpty()) {
                        return create(planName, subject, priority, effectiveFrom, effectiveTo);
                    }
                    if (!replaceExisting) {
                        return Mono.error(conflict("主体 [" + subject.name() + "] 已存在有效套餐绑定。"));
                    }
                    var primary = existing.get(0);
                    primary.getSpec().setPlan(planName);
                    primary.getSpec().setPriority(priority);
                    primary.getSpec().setEffectiveFrom(effectiveFrom);
                    primary.getSpec().setEffectiveTo(effectiveTo);
                    var duplicates = Flux.fromIterable(existing.subList(1, existing.size()))
                            .concatMap(client::delete)
                            .then();
                    return client.update(primary)
                            .flatMap(updated -> duplicates.thenReturn(updated))
                            .map(PlanBindingService::summary);
                });
    }

    private Mono<BindingSummary> create(
            String planName,
            BindingSubject subject,
            int priority,
            @Nullable Instant effectiveFrom,
            @Nullable Instant effectiveTo) {
        var name = bindingName(subject);
        return client.get(AiPlanBinding.class, name)
                .flatMap(existing -> {
                    existing.getSpec().setPlan(planName);
                    existing.getSpec().setPriority(priority);
                    existing.getSpec().setEffectiveFrom(effectiveFrom);
                    existing.getSpec().setEffectiveTo(effectiveTo);
                    return client.update(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    var binding = new AiPlanBinding();
                    var metadata = new Metadata();
                    metadata.setName(name);
                    binding.setMetadata(metadata);
                    binding.getSpec().setPlan(planName);
                    binding.getSpec().setPriority(priority);
                    binding.getSpec().setEffectiveFrom(effectiveFrom);
                    binding.getSpec().setEffectiveTo(effectiveTo);
                    var bindingSubject = new AiPlanBinding.Subject();
                    bindingSubject.setKind(subject.kind());
                    bindingSubject.setName(subject.name());
                    binding.getSpec().setSubject(bindingSubject);
                    return client.create(binding);
                }))
                .map(PlanBindingService::summary);
    }

    private Flux<AiPlanBinding> effectiveBindings(BindingSubject subject, Instant now) {
        var options = ListOptions.builder()
                .andQuery(in("spec.subject.name", subject.name()))
                .build();
        return client.listAll(AiPlanBinding.class, options, defaultSort())
                .filter(binding -> binding.getMetadata() != null
                        && binding.getSpec() != null
                        && binding.getSpec().getSubject() != null
                        && subject.kind() == binding.getSpec().getSubject().getKind()
                        && subject.name().equals(binding.getSpec().getSubject().getName())
                        && effective(binding, now))
                .sort(Comparator.comparing((AiPlanBinding binding) -> binding.getMetadata().getName()));
    }

    private static boolean effective(AiPlanBinding binding, Instant now) {
        var from = binding.getSpec().getEffectiveFrom();
        var to = binding.getSpec().getEffectiveTo();
        return (from == null || !from.isAfter(now)) && (to == null || to.isAfter(now));
    }

    private static String bindingName(BindingSubject subject) {
        return "aiplanbinding-" + subject.kind().name().toLowerCase(Locale.ROOT) + "-" + subject.name();
    }

    private static BindingSummary summary(AiPlanBinding binding) {
        return new BindingSummary(
                binding.getMetadata().getName(),
                binding.getSpec().getPlan(),
                binding.getSpec().getSubject().getKind(),
                binding.getSpec().getSubject().getName(),
                binding.getSpec().getPriority(),
                binding.getSpec().getEffectiveFrom(),
                binding.getSpec().getEffectiveTo());
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, CODE_BINDING_INVALID, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, CODE_BINDING_CONFLICT, message);
    }

    public record BulkBindRequest(
            String plan,
            List<BindingSubject> subjects,
            int priority,
            boolean replaceExisting,
            @Nullable Instant effectiveFrom,
            @Nullable Instant effectiveTo) {

        public BulkBindRequest(
                String plan, List<BindingSubject> subjects, int priority, boolean replaceExisting) {
            this(plan, subjects, priority, replaceExisting, null, null);
        }
    }

    public record BindingSubject(AiPlanBinding.SubjectKind kind, String name) {
    }

    public record BindingSummary(
            String name,
            String plan,
            AiPlanBinding.SubjectKind kind,
            String subject,
            int priority,
            @Nullable Instant effectiveFrom,
            @Nullable Instant effectiveTo) {
    }

    public record BulkBindResult(String plan, List<BindingSummary> bindings) {
    }
}