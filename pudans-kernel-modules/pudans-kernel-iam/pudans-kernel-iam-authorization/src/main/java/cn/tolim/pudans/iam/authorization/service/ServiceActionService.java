package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.dto.ServiceActionRequest;
import cn.tolim.pudans.iam.authorization.dto.ServiceActionView;
import cn.tolim.pudans.iam.authorization.entity.ServiceActionEntity;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.ResourceTypeRepository;
import cn.tolim.pudans.iam.authorization.repository.ServiceActionRepository;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ServiceActionService {

    private final ServiceActionRepository actionRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final AppRepository appRepository;
    private final AuthzDecisionCache decisionCache;

    // Registers an action declared by an application
    // Parameter request: ServiceActionRequest
    // Returns: ServiceActionView
    @Transactional(rollbackFor = Exception.class)
    public ServiceActionView register(ServiceActionRequest request) {
        AppEntity application = requireApplication(request.getAppCode());
        String action = normalizeAction(application.getCode(), request.getAction());
        if (actionRepository.existsByAppIdAndAction(application.getId(), action)) {
            throw AuthorizationErrors.conflict("Action '" + action
                    + "' is already registered by application '" + application.getCode() + "'");
        }
        String resourceType = normalizeResourceType(application.getCode(), request.getResourceType());
        validateResourceType(application, resourceType);
        ServiceActionEntity entity = new ServiceActionEntity()
                .setAppId(application.getId())
                .setAction(action)
                .setResourceType(resourceType)
                .setDescription(request.getDescription())
                .setRiskLevel(EnumSupport.riskLevel(request.getRiskLevel()))
                .setStatus(EnumSupport.status(request.getStatus()));
        EntityAuditSupport.stampCreate(entity);
        ServiceActionEntity saved = actionRepository.save(entity);
        decisionCache.invalidateAll();
        return toView(saved, application.getCode());
    }

    // Updates an action declaration
    // Parameter id: String action ID
    // Parameter request: ServiceActionRequest
    // Returns: ServiceActionView
    @Transactional(rollbackFor = Exception.class)
    public ServiceActionView update(String id, ServiceActionRequest request) {
        ServiceActionEntity entity = requireAction(id);
        AppEntity application = requireApplication(request.getAppCode());
        if (!application.getId().equals(entity.getAppId())) {
            throw AuthorizationErrors.badRequest("An action cannot be moved to another application");
        }
        String action = normalizeAction(application.getCode(), request.getAction());
        if (!action.equals(entity.getAction())) {
            throw AuthorizationErrors.badRequest("An action name cannot be changed");
        }
        String resourceType = normalizeResourceType(application.getCode(), request.getResourceType());
        validateResourceType(application, resourceType);
        entity.setResourceType(resourceType)
                .setDescription(request.getDescription())
                .setRiskLevel(EnumSupport.riskLevel(request.getRiskLevel()))
                .setStatus(EnumSupport.status(request.getStatus()));
        EntityAuditSupport.stampUpdate(entity);
        ServiceActionEntity saved = actionRepository.save(entity);
        decisionCache.invalidateAll();
        return toView(saved, application.getCode());
    }

    // Deletes an action declaration
    // Parameter id: String action ID
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ServiceActionEntity entity = requireAction(id);
        actionRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Lists actions declared by an application
    // Parameter appCode: String application code
    // Returns: List<ServiceActionView>
    public List<ServiceActionView> listByApp(String appCode) {
        AppEntity application = requireApplication(appCode);
        return actionRepository.findAllByAppId(application.getId()).stream()
                .map(action -> toView(action, application.getCode())).toList();
    }

    // Returns an action declaration by ID
    // Parameter id: String action ID
    // Returns: ServiceActionView
    public ServiceActionView get(String id) {
        ServiceActionEntity entity = requireAction(id);
        AppEntity application = appRepository.findById(entity.getAppId())
                .orElseThrow(() -> AuthorizationErrors.notFound("Application '" + entity.getAppId()
                        + "' does not exist"));
        return toView(entity, application.getCode());
    }

    // Returns the action declaration with the given ID or throws when it does not exist
    // Parameter id: String action ID
    // Returns: ServiceActionEntity
    public ServiceActionEntity requireAction(String id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Service action '" + id
                        + "' does not exist"));
    }

    private AppEntity requireApplication(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw AuthorizationErrors.badRequest("appCode must not be blank");
        }
        return appRepository.findByCode(appCode.trim())
                .orElseThrow(() -> AuthorizationErrors.notFound("Application '" + appCode
                        + "' does not exist"));
    }

    private void validateResourceType(AppEntity application, String resourceType) {
        if (resourceType == null) {
            return;
        }
        if (!resourceTypeRepository.existsByAppIdAndCode(application.getId(), resourceType)) {
            throw AuthorizationErrors.badRequest("Resource type '" + resourceType
                    + "' is not registered by application '" + application.getCode() + "'");
        }
    }

    private static String normalizeAction(String appCode, String action) {
        if (action == null || action.isBlank()) {
            throw AuthorizationErrors.badRequest("action must not be blank");
        }
        String value = action.trim();
        int separator = value.indexOf(':');
        if (separator < 0) {
            return appCode + ":" + value;
        }
        if (separator == value.length() - 1) {
            throw AuthorizationErrors.badRequest("action must not end with ':'");
        }
        if (!value.substring(0, separator).equals(appCode)) {
            throw AuthorizationErrors.badRequest("Action '" + value
                    + "' must belong to application '" + appCode + "'");
        }
        return value;
    }

    private static String normalizeResourceType(String appCode, String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        String value = resourceType.trim();
        int separator = value.indexOf(':');
        if (separator < 0) {
            return appCode + ":" + value;
        }
        if (!value.substring(0, separator).equals(appCode)) {
            throw AuthorizationErrors.badRequest("Resource type '" + value
                    + "' must belong to application '" + appCode + "'");
        }
        return value;
    }

    private static ServiceActionView toView(ServiceActionEntity entity, String appCode) {
        ServiceActionView view = new ServiceActionView();
        view.setId(entity.getId());
        view.setAppId(entity.getAppId());
        view.setAppCode(appCode);
        view.setAction(entity.getAction());
        view.setResourceType(entity.getResourceType());
        view.setDescription(entity.getDescription());
        view.setRiskLevel(entity.getRiskLevel() == null ? null : entity.getRiskLevel().getCode());
        view.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        return view;
    }
}