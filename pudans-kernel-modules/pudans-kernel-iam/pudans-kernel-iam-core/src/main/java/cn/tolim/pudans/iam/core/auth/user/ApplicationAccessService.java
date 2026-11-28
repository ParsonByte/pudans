package cn.tolim.pudans.iam.core.auth.user;

import cn.tolim.pudans.contract.iam.dto.app.ApplicationAccessResponse;
import cn.tolim.pudans.contract.iam.dto.app.MyApplicationSummary;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppAccessPolicyEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppPolicySubjectType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import cn.tolim.pudans.iam.core.auth.exception.AppAccessDeniedException;
import cn.tolim.pudans.iam.core.auth.exception.AppUnavailableException;
import cn.tolim.pudans.iam.infra.security.userdetails.Application;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ApplicationAccessService {

    public static final String REASON_ADMIN = "administrator";
    public static final String REASON_ALL_ACCESS = "all_access";
    public static final String REASON_POLICY = "access_policy";
    public static final String REASON_DEFAULT_DENY = "default_deny";

    private final AppRepository appRepository;
    private final AppAccessPolicyRepository appAccessPolicyRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final UserAccountService userAccountService;

    public ApplicationAccessService(AppRepository appRepository,
                                    AppAccessPolicyRepository appAccessPolicyRepository,
                                    OrganizationMemberRepository organizationMemberRepository,
                                    UserAccountService userAccountService) {
        this.appRepository = appRepository;
        this.appAccessPolicyRepository = appAccessPolicyRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.userAccountService = userAccountService;
    }

    // Resolves an enabled application by code, throwing when it is missing or disabled
    // Parameter appCode: String
    // Returns: AppEntity
    public AppEntity requireEnabledApp(String appCode) {
        AppEntity app = appRepository.findByCode(appCode)
                .orElseThrow(() -> new AppUnavailableException(appCode));
        if (!Boolean.TRUE.equals(app.getEnabled())) {
            throw new AppUnavailableException("app_disabled", "Application [" + appCode + "] is disabled");
        }
        return app;
    }

    // Resolves an application that is ready to receive tickets
    // Parameter appCode: String
    // Returns: AppEntity
    public AppEntity requireTicketReadyApp(String appCode) {
        AppEntity app = requireEnabledApp(appCode);
        if (!Boolean.TRUE.equals(app.getConfigured())) {
            throw new AppUnavailableException("app_not_configured",
                    "Application [" + appCode + "] is not fully configured");
        }
        return app;
    }

    // Checks whether a global login session may access an application
    // Parameter session: LoginSessionEntity
    // Parameter app: AppEntity
    // Returns: boolean
    public boolean canAccess(LoginSessionEntity session, AppEntity app) {
        if (session == null || app == null || app.getId() == null) {
            return false;
        }
        return canAccess(session.getUserId(), session.getOrgId(), session.getUserType(), app);
    }

    // Enforces the application level access policy
    // Parameter session: LoginSessionEntity
    // Parameter app: AppEntity
    public void assertCanAccess(LoginSessionEntity session, AppEntity app) {
        if (!canAccess(session, app)) {
            throw new AppAccessDeniedException(app == null ? null : app.getCode());
        }
    }

    // Checks application level access for explicit principal attributes
    // Parameter userId: String
    // Parameter orgId: String
    // Parameter userType: String
    // Parameter appCode: String
    // Returns: ApplicationAccessResponse
    public ApplicationAccessResponse checkAccess(String userId, String orgId, String userType,
                                                 String appCode) {
        ApplicationAccessResponse response = new ApplicationAccessResponse();
        response.setAppCode(appCode);
        response.setUserId(userId);
        response.setOrgId(orgId);
        Optional<AppEntity> optional = appRepository.findByCode(appCode);
        if (optional.isEmpty()) {
            response.setAllowed(Boolean.FALSE);
            response.setReason("app_not_found");
            return response;
        }
        AppEntity app = optional.get();
        if (!Boolean.TRUE.equals(app.getEnabled())) {
            response.setAllowed(Boolean.FALSE);
            response.setReason("app_disabled");
            return response;
        }
        if (IamAuthTypes.isAdmin(userType)) {
            response.setAllowed(Boolean.TRUE);
            response.setReason(REASON_ADMIN);
            return response;
        }
        if (AuthorizationType.ALL_ACCESS.equals(app.getAuthorizationType())) {
            response.setAllowed(Boolean.TRUE);
            response.setReason(REASON_ALL_ACCESS);
            return response;
        }
        boolean allowed = hasPolicy(app.getId(), userId, orgId, userType);
        response.setAllowed(allowed);
        response.setReason(allowed ? REASON_POLICY : REASON_DEFAULT_DENY);
        return response;
    }

    // Lists applications the session principal may open, filtered by the access policy
    // Parameter session: LoginSessionEntity
    // Returns: List
    public List<MyApplicationSummary> listAccessibleApplications(LoginSessionEntity session) {
        List<MyApplicationSummary> applications = new ArrayList<>();
        if (session == null) {
            return applications;
        }
        for (AppEntity app : appRepository.findAll()) {
            if (!Boolean.TRUE.equals(app.getEnabled())) {
                continue;
            }
            if (!canAccess(session, app)) {
                continue;
            }
            MyApplicationSummary summary = new MyApplicationSummary();
            summary.setAppCode(app.getCode());
            summary.setName(app.getName());
            summary.setIcon(app.getIcon());
            summary.setBaseUrl(app.getInitLoginUrl());
            summary.setProtocol(app.getProtocol() == null ? null : app.getProtocol().getCode());
            summary.setSort(0);
            applications.add(summary);
        }
        applications.sort((left, right) -> {
            String leftName = left.getName() == null ? "" : left.getName();
            String rightName = right.getName() == null ? "" : right.getName();
            return leftName.compareToIgnoreCase(rightName);
        });
        return applications;
    }

    // Lists the applications the session principal may open as security principal objects
    // Parameter session: LoginSessionEntity
    // Returns: List
    public List<Application> listApplicationPrincipals(LoginSessionEntity session) {
        List<Application> applications = new ArrayList<>();
        if (session == null) {
            return applications;
        }
        for (AppEntity app : appRepository.findAll()) {
            if (!Boolean.TRUE.equals(app.getEnabled()) || !canAccess(session, app)) {
                continue;
            }
            applications.add(new Application(app.getId(), app.getCode(), app.getName(), null));
        }
        return applications;
    }

    // Checks application access using the explicit principal attributes
    // Parameter userId: String
    // Parameter orgId: String
    // Parameter userType: String
    // Parameter app: AppEntity
    // Returns: boolean
    public boolean canAccess(String userId, String orgId, String userType, AppEntity app) {
        if (app == null) {
            return false;
        }
        if (IamAuthTypes.isAdmin(userType)) {
            return true;
        }
        if (AuthorizationType.ALL_ACCESS.equals(app.getAuthorizationType())) {
            return true;
        }
        return hasPolicy(app.getId(), userId, orgId, userType);
    }

    private boolean hasPolicy(String appId, String userId, String orgId, String userType) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (isEnabledPolicy(appId, userId, AppPolicySubjectType.USER)) {
            return true;
        }
        for (String groupId : userAccountService.groupIds(userId)) {
            if (isEnabledPolicy(appId, groupId, AppPolicySubjectType.USER_GROUP)) {
                return true;
            }
        }
        Set<String> organizationIds = new LinkedHashSet<>();
        if (orgId != null && !orgId.isBlank()) {
            organizationIds.add(orgId);
        }
        for (OrganizationMemberEntity membership : organizationMemberRepository.findAllByUserId(userId)) {
            organizationIds.add(membership.getOrgId());
        }
        for (String organizationId : organizationIds) {
            if (isEnabledPolicy(appId, organizationId, AppPolicySubjectType.ORGANIZATION)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEnabledPolicy(String appId, String subjectId, AppPolicySubjectType subjectType) {
        return appAccessPolicyRepository
                .findByAppIdAndSubjectIdAndSubjectType(appId, subjectId, subjectType)
                .map(AppAccessPolicyEntity::getEnabled)
                .map(Boolean.TRUE::equals)
                .orElse(Boolean.FALSE);
    }
}