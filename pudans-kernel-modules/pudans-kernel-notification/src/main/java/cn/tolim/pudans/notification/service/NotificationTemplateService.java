package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.dto.TemplateRequest;
import cn.tolim.pudans.notification.dto.TemplateResponse;
import cn.tolim.pudans.notification.entity.NotificationTemplateEntity;
import cn.tolim.pudans.notification.exception.NotificationResourceNotFoundException;
import cn.tolim.pudans.notification.exception.NotificationValidationException;
import cn.tolim.pudans.notification.repository.NotificationTemplateRepository;
import cn.tolim.pudans.notification.support.NotificationAudit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class NotificationTemplateService {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateService(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new NotificationValidationException(
                    "Notification template already exists: " + request.code());
        }
        NotificationTemplateEntity entity = new NotificationTemplateEntity();
        entity.setCode(request.code().trim());
        apply(entity, request);
        return TemplateResponse.from(repository.save(NotificationAudit.touch(entity)));
    }

    @Transactional
    public TemplateResponse update(String id, TemplateRequest request) {
        NotificationTemplateEntity entity = getEntity(id);
        if (!entity.getCode().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new NotificationValidationException(
                    "Notification template already exists: " + request.code());
        }
        entity.setCode(request.code().trim());
        apply(entity, request);
        return TemplateResponse.from(repository.save(NotificationAudit.touch(entity)));
    }

    @Transactional
    public void delete(String id) {
        NotificationTemplateEntity entity = getEntity(id);
        repository.delete(entity);
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(String id) {
        return TemplateResponse.from(getEntity(id));
    }

    @Transactional(readOnly = true)
    public NotificationTemplateEntity getEnabledByCode(String code) {
        return repository.findByCodeAndEnabledTrue(code)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Enabled notification template not found: " + code));
    }

    public NotificationTemplateEntity getEntity(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotificationResourceNotFoundException(
                        "Notification template not found: " + id));
    }

    private void apply(NotificationTemplateEntity entity, TemplateRequest request) {
        entity.setName(request.name().trim());
        entity.setChannel(request.channel());
        entity.setSubject(request.subject());
        entity.setContent(request.content());
        entity.setProviderTemplateCode(request.providerTemplateCode());
        entity.setLanguage(request.language() == null ? Locale.ENGLISH.getLanguage()
                : request.language().trim());
        entity.setEnabled(request.enabled() == null || request.enabled());
    }
}