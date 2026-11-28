package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.dto.ProviderRequest;
import cn.tolim.pudans.notification.dto.ProviderResponse;
import cn.tolim.pudans.notification.dto.ProviderTestRequest;
import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.exception.NotificationResourceNotFoundException;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.repository.NotificationProviderRepository;
import cn.tolim.pudans.notification.security.NotificationSecretCipher;
import cn.tolim.pudans.notification.sender.NotificationMessage;
import cn.tolim.pudans.notification.sender.NotificationProviderConfig;
import cn.tolim.pudans.notification.sender.SendResult;
import cn.tolim.pudans.notification.support.NotificationAudit;
import cn.tolim.pudans.notification.support.NotificationJson;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationProviderService {

    private static final String MASK = "******";

    private final NotificationProviderRepository repository;
    private final NotificationProviderConfigValidator validator;
    private final NotificationProviderSelector selector;
    private final NotificationSecretCipher secretCipher;

    public NotificationProviderService(NotificationProviderRepository repository,
                                       NotificationProviderConfigValidator validator,
                                       NotificationProviderSelector selector,
                                       NotificationSecretCipher secretCipher) {
        this.repository = repository;
        this.validator = validator;
        this.selector = selector;
        this.secretCipher = secretCipher;
    }

    @Transactional
    public ProviderResponse create(ProviderRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new NotificationValidationException(
                    "Notification provider already exists: " + request.code());
        }
        Map<String, String> configuration = request.configuration() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(request.configuration());
        validator.validate(request.channel(), request.provider(), configuration);
        NotificationProviderEntity entity = new NotificationProviderEntity();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setChannel(request.channel());
        entity.setProvider(request.provider());
        entity.setPriority(request.priority() == null ? 100 : request.priority());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setConfiguration(secretCipher.encrypt(NotificationJson.writeMap(configuration)));
        NotificationProviderEntity saved = repository.save(NotificationAudit.touch(entity));
        return toResponse(saved, configuration);
    }

    @Transactional
    public ProviderResponse update(String id, ProviderRequest request) {
        NotificationProviderEntity entity = getEntity(id);
        if (!entity.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new NotificationValidationException(
                    "Notification provider already exists: " + request.code());
        }
        Map<String, String> existing;
        try {
            existing = readConfigurationMap(entity);
        } catch (NotificationValidationException exception) {
            existing = new LinkedHashMap<>();
        }
        Map<String, String> configuration = mergeConfiguration(existing, request.configuration());
        validator.validate(request.channel(), request.provider(), configuration);
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setChannel(request.channel());
        entity.setProvider(request.provider());
        entity.setPriority(request.priority() == null
                ? (entity.getPriority() == null ? 100 : entity.getPriority()) : request.priority());
        entity.setEnabled(request.enabled() == null
                ? Boolean.TRUE.equals(entity.getEnabled()) : request.enabled());
        entity.setConfiguration(secretCipher.encrypt(NotificationJson.writeMap(configuration)));
        NotificationProviderEntity saved = repository.save(NotificationAudit.touch(entity));
        return toResponse(saved, configuration);
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getEntity(id));
    }

    @Transactional(readOnly = true)
    public ProviderResponse get(String id) {
        NotificationProviderEntity entity = getEntity(id);
        return ProviderResponse.from(entity, maskedConfiguration(entity));
    }

    @Transactional(readOnly = true)
    public Page<ProviderResponse> list(NotificationChannel channel,
                                       Boolean enabled, String keyword, Pageable pageable) {
        Specification<NotificationProviderEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (channel != null) {
                predicates.add(builder.equal(root.get("channel"), channel));
            }
            if (enabled != null) {
                predicates.add(builder.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("code")), pattern),
                        builder.like(builder.lower(root.get("name")), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable).map(entity -> ProviderResponse.from(entity,
                maskedConfiguration(entity)));
    }

    @Transactional(readOnly = true)
    public SendResult test(String id, ProviderTestRequest request) {
        NotificationProviderEntity entity = getEntity(id);
        NotificationProviderSelector.Selection selection = selector.resolve(entity);
        NotificationMessage message = NotificationMessage.builder()
                .recipient(request.recipient())
                .subject(request.subject())
                .content(request.content())
                .providerTemplateCode(request.providerTemplateCode())
                .parameters(request.parameters())
                .templateParameters(request.templateParameters())
                .build();
        return selection.sender().send(message, selection.config());
    }

    public NotificationProviderEntity getEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification provider not found: " + id));
    }

    public Map<String, String> maskedConfiguration(NotificationProviderEntity entity) {
        try {
            return selector.readConfiguration(entity).masked();
        } catch (RuntimeException exception) {
            return Map.of("configurationError", "Unable to read encrypted configuration");
        }
    }

    private Map<String, String> readConfigurationMap(NotificationProviderEntity entity) {
        String stored = entity.getConfiguration();
        if (stored == null || stored.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            String json = secretCipher.isEncrypted(stored) ? secretCipher.decrypt(stored) : stored;
            return new LinkedHashMap<>(NotificationJson.readMap(json));
        } catch (RuntimeException exception) {
            throw new NotificationValidationException("Unable to read provider configuration");
        }
    }

    private Map<String, String> mergeConfiguration(Map<String, String> existing,
                                                   Map<String, String> requested) {
        if (requested == null) {
            return new LinkedHashMap<>(existing);
        }
        Map<String, String> merged = new LinkedHashMap<>(existing);
        requested.forEach((key, value) -> {
            if (value == null || value.isBlank() || MASK.equals(value)) {
                if (!existing.containsKey(key)) {
                    merged.put(key, value);
                }
                return;
            }
            merged.put(key, value);
        });
        return merged;
    }

    private ProviderResponse toResponse(NotificationProviderEntity entity,
                                        Map<String, String> configuration) {
        return ProviderResponse.from(entity,
                new NotificationProviderConfig(configuration).masked());
    }
}