package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppGroupEntity;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.portal.dto.PortalAppGroupView;
import cn.tolim.pudans.iam.portal.dto.PortalAppView;
import cn.tolim.pudans.iam.portal.dto.PortalAppsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PortalAppService {

    private final PortalSessionService portalSessionService;
    private final ApplicationAccessService applicationAccessService;
    private final AppRepository appRepository;
    private final AppGroupAssociationRepository appGroupAssociationRepository;
    private final AppGroupRepository appGroupRepository;
    private final PortalUrlResolver portalUrlResolver;

    public PortalAppService(PortalSessionService portalSessionService,
                            ApplicationAccessService applicationAccessService,
                            AppRepository appRepository,
                            AppGroupAssociationRepository appGroupAssociationRepository,
                            AppGroupRepository appGroupRepository, PortalUrlResolver portalUrlResolver) {
        this.portalSessionService = portalSessionService;
        this.applicationAccessService = applicationAccessService;
        this.appRepository = appRepository;
        this.appGroupAssociationRepository = appGroupAssociationRepository;
        this.appGroupRepository = appGroupRepository;
        this.portalUrlResolver = portalUrlResolver;
    }

    // Lists enabled applications the current user may enter according to the application access policy
    // Parameter request: HttpServletRequest current request
    // Returns: PortalAppsResponse
    public PortalAppsResponse getAuthorizedApps(HttpServletRequest request) {
        PortalAppsResponse response = new PortalAppsResponse();
        Optional<PortalIdentity> optional = portalSessionService.resolve(request);
        response.setAuthenticated(optional.isPresent());
        if (optional.isEmpty()) {
            return response;
        }
        PortalIdentity identity = optional.get();
        List<AppEntity> apps = appRepository.findAll();
        if (apps == null || apps.isEmpty()) {
            return response;
        }
        String loginUrl = portalUrlResolver.resolveLoginUrl(ServletContextService.getServerUrl(request));
        Map<String, PortalAppView> distinct = new LinkedHashMap<>();
        for (AppEntity app : apps) {
            if (app == null || StringUtils.isBlank(app.getId()) || StringUtils.isBlank(app.getCode())) {
                continue;
            }
            if (!Boolean.TRUE.equals(app.getEnabled())) {
                continue;
            }
            if (!canAccess(identity, app)) {
                continue;
            }
            distinct.putIfAbsent(app.getId(), toView(app, loginUrl));
        }
        List<PortalAppView> views = new ArrayList<>(distinct.values());
        views.sort(Comparator.comparing(
                (PortalAppView view) -> StringUtils.defaultString(view.getName()),
                String.CASE_INSENSITIVE_ORDER));
        response.setApps(views);
        return response;
    }

    private boolean canAccess(PortalIdentity identity, AppEntity app) {
        if (AuthorizationType.ALL_ACCESS.equals(app.getAuthorizationType())) {
            return true;
        }
        return applicationAccessService.canAccess(identity.userId(), identity.organizationId(),
                identity.userType(), app);
    }

    private PortalAppView toView(AppEntity app, String loginUrl) {
        PortalAppView view = new PortalAppView();
        view.setAppId(app.getId());
        view.setAppCode(app.getCode());
        view.setName(app.getName());
        view.setIcon(app.getIcon());
        view.setProtocol(app.getProtocol() == null ? null : app.getProtocol().getCode());
        view.setType(app.getType() == null ? null : app.getType().getCode());
        view.setTemplate(app.getTemplate());
        view.setLaunchUrl(launchUrl(app, loginUrl));
        view.setGroups(resolveGroups(app));
        return view;
    }

    private String launchUrl(AppEntity app, String loginUrl) {
        if (StringUtils.isNotBlank(app.getInitLoginUrl())) {
            return app.getInitLoginUrl();
        }
        String separator = loginUrl.contains("?") ? "&" : "?";
        return loginUrl + separator + "app="
                + URLEncoder.encode(app.getCode(), StandardCharsets.UTF_8);
    }

    private List<PortalAppGroupView> resolveGroups(AppEntity app) {
        List<String> groupIds = appGroupAssociationRepository.findGroupIdByAppId(app.getId());
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        List<String> distinctGroupIds = groupIds.stream()
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
        if (distinctGroupIds.isEmpty()) {
            return List.of();
        }
        Map<String, AppGroupEntity> entities = appGroupRepository.findAllById(distinctGroupIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AppGroupEntity::getId, group -> group, (left, right) -> left));
        List<PortalAppGroupView> groups = new ArrayList<>();
        for (String groupId : distinctGroupIds) {
            PortalAppGroupView groupView = new PortalAppGroupView();
            groupView.setId(groupId);
            AppGroupEntity entity = entities.get(groupId);
            if (entity != null) {
                groupView.setCode(entity.getCode());
                groupView.setName(entity.getName());
            }
            groups.add(groupView);
        }
        return groups;
    }
}