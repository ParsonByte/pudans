package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.core.extension.ai.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class L3PipelineService {

    private final JevGate jevGate;

    private final PersonaTrigger personaTrigger;

    private final MemoryPromptResolver promptResolver;

    private final AiMemoryL3Repository l3Repository;

    private final AiMemoryGenerationLogRepository generationLogRepository;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Mono<Result> process(
            Request request, JevGate.ChangeClassifier classifier, PersonaGenerator generator) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(generator, "generator");
        var triggerReason = personaTrigger.evaluate(request.triggerState());
        var promptContext = MemoryPromptContext.compose(promptResolver, AiMemoryPrompt.Layer.L3, request.prompt());
        return l3Repository
                .findByScope(request.teamId(), request.agentId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(current -> {
                    if (triggerReason.triggersGeneration()) {
                        return generate(request, generator, promptContext, triggerReason, null, current);
                    }
                    var jevDecision = jevGate.evaluate(
                            current.map(L3Persona::personaText).orElse(null),
                            request.changedScenes(),
                            classifier);
                    if (jevDecision.decision() != JevDecisionType.IMMEDIATE) {
                        return Mono.just(new Result(
                                triggerReason, jevDecision, promptContext.systemPrompt(), null, false));
                    }
                    return generate(request, generator, promptContext, triggerReason, jevDecision, current);
                });
    }

    private Mono<Result> generate(
            Request request,
            PersonaGenerator generator,
            MemoryPromptContext promptContext,
            PersonaTrigger.Reason triggerReason,
            JevDecision jevDecision,
            Optional<L3Persona> current) {
        var generated = generator.generate(
                promptContext.systemPrompt(), current.orElse(null), request.changedScenes());
        if (generated == null) {
            return Mono.error(new IllegalStateException("Persona 生成结果为空"));
        }
        var now = clock.instant();
        var persona = new L3Persona(
                null,
                request.teamId(),
                request.agentId(),
                generated.personaJson(),
                generated.personaText(),
                generated.sourceL1Count(),
                generated.sourceL2Count(),
                generated.confidence(),
                now);
        return l3Repository
                .upsert(persona)
                .flatMap(inserted -> generationLogRepository
                        .insert(promptContext.toGenerationLog(
                                inserted.id(),
                                request.teamId(),
                                request.agentId(),
                                null,
                                null,
                                request.generation()))
                        .thenReturn(new Result(
                                triggerReason, jevDecision, promptContext.systemPrompt(), inserted, true)));
    }

    @FunctionalInterface
    public interface PersonaGenerator {
        GeneratedPersona generate(
                String systemPrompt, L3Persona currentPersona, List<JevGate.ChangedScene> changedScenes);
    }

    public record GeneratedPersona(
            Map<String, Object> personaJson,
            String personaText,
            int sourceL1Count,
            int sourceL2Count,
            BigDecimal confidence) {

        public GeneratedPersona {
            personaJson = personaJson == null ? Map.of() : Map.copyOf(personaJson);
            Objects.requireNonNull(personaText, "personaText");
            if (sourceL1Count < 0 || sourceL2Count < 0) {
                throw new IllegalArgumentException("来源计数不能为负数");
            }
            Objects.requireNonNull(confidence, "confidence");
            if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("confidence 必须在 0 到 1 之间");
            }
        }
    }

    public record Request(
            String teamId,
            String agentId,
            PersonaTrigger.State triggerState,
            List<JevGate.ChangedScene> changedScenes,
            MemoryPromptInput prompt,
            MemoryGenerationInput generation) {

        public Request {
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            Objects.requireNonNull(triggerState, "triggerState");
            changedScenes = changedScenes == null ? List.of() : List.copyOf(changedScenes);
            prompt = prompt == null ? MemoryPromptInput.none() : prompt;
            generation = generation == null ? MemoryGenerationInput.empty() : generation;
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record Result(
            PersonaTrigger.Reason triggerReason,
            JevDecision jevDecision,
            String systemPrompt,
            L3Persona persona,
            boolean generated) {}
}
