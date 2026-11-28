package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.dto.ResourceTypeRequest;
import cn.tolim.pudans.iam.authorization.dto.ResourceTypeView;
import cn.tolim.pudans.iam.authorization.entity.ResourceTypeEntity;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.authorization.repository.ResourceTypeRepository;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;
    private final AppRepository appRepository;
    private final AuthzDecisionCache decisionCache;

    // Registers a resource type declared by an application
    // Parameter request: ResourceTypeRequest
    // Returns: ResourceTypeView
    @Transactional(rollbackFor = Exception.class)
    public ResourceTypeView register(ResourceTypeRequest request) {
        AppEntity application = requireApplication(request.getAppCode());
        String code = normalizeCode(application.getCode(), request.getCode());
        if (resourceTypeRepository.existsByAppIdAndCode(application.getId(), code)) {
            throw AuthorizationErrors.conflict("Resource type '" + code
                    + "' is already registered by application '" + application.getCode() + "'");
        }
        ResourceTypeEntity entity = new ResourceTypeEntity()
                .setAppId(application.getId())
                .setCode(code)
                .setName(request.getName().trim())
                .setPattern(request.getPattern().trim())
                .setStatus(EnumSupport.status(request.getStatus()));
        EntityAuditSupport.stampCreate(entity);
        ResourceTypeEntity saved = resourceTypeRepository.save(entity);
        decisionCache.invalidateAll();
        return toView(saved, application.getCode());
    }

    // Updates a resource type declaration
    // Parameter id: String resource type ID
    // Parameter request: ResourceTypeRequest
    // Returns: ResourceTypeView
    @Transactional(rollbackFor = Exception.class)
    public ResourceTypeView update(String id, ResourceTypeRequest request) {
        ResourceTypeEntity entity = requireResourceType(id);
        AppEntity application = requireApplication(request.getAppCode());
        if (!application.getId().equals(entity.getAppId())) {
            throw AuthorizationErrors.badRequest("A resource type cannot be moved to another application");
        }
        String code = normalizeCode(application.getCode(), request.getCode());
        if (!code.equals(entity.getCode())) {
            throw AuthorizationErrors.badRequest("A resource type code cannot be changed");
        }
        entity.setName(request.getName().trim())
                .setPattern(request.getPattern().trim())
                .setStatus(EnumSupport.status(request.getStatus()));
        EntityAuditSupport.stampUpdate(entity);
        ResourceTypeEntity saved = resourceTypeRepository.save(entity);
        decisionCache.invalidateAll();
        return toView(saved, application.getCode());
    }

    // Deletes a resource type declaration
    // Parameter id: String resource type ID
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ResourceTypeEntity entity = requireResourceType(id);
        resourceTypeRepository.delete(entity);
        decisionCache.invalidateAll();
    }

    // Lists resource types declared by an application
    // Parameter appCode: String application code
    // Returns: List<ResourceTypeView>
    public List<ResourceTypeView> listByApp(String appCode) {
        AppEntity application = requireApplication(appCode);
        return resourceTypeRepository.findAllByAppId(application.getId()).stream()
                .map(resourceType -> toView(resourceType, application.getCode())).toList();
    }

    // Returns a resource type declaration by ID
    // Parameter id: String resource type ID
    // Returns: ResourceTypeView
    public ResourceTypeView get(String id) {
        ResourceTypeEntity entity = requireResourceType(id);
        AppEntity application = appRepository.findById(entity.getAppId())
                .orElseThrow(() -> AuthorizationErrors.notFound("Application '" + entity.getAppId()
                        + "' does not exist"));
        return toView(entity, application.getCode());
    }

    // Returns the resource type with the given ID or throws when it does not exist
    // Parameter id: String resource type ID
    // Returns: ResourceTypeEntity
    public ResourceTypeEntity requireResourceType(String id) {
        return resourceTypeRepository.findById(id)
                .orElseThrow(() -> AuthorizationErrors.notFound("Resource type '" + id
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

    private static String normalizeCode(String appCode, String code) {
        if (code == null || code.isBlank()) {
            throw AuthorizationErrors.badRequest("code must not be blank");
        }
        String value = code.trim();
        int separator = value.indexOf(':');
        if (separator < 0) {
            return appCode + ":" + value;
        }
        if (separator == value.length() - 1) {
            throw AuthorizationErrors.badRequest("code must not end with ':'");
        }
        if (!value.substring(0, separator).equals(appCode)) {
            throw AuthorizationErrors.badRequest("Resource type '" + value
                    + "' must belong to application '" + appCode + "'");
        }
        return value;
    }

    private static ResourceTypeView toView(ResourceTypeEntity entity, String appCode) {
        ResourceTypeView view = new ResourceTypeView();
        view.setId(entity.getId());
        view.setAppId(entity.getAppId());
        view.setAppCode(appCode);
        view.setCode(entity.getCode());
        view.setName(entity.getName());
        view.setPattern(entity.getPattern());
        view.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
        return view;
    }
}