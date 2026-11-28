package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.communal.core.security.AuthenticatedUser;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import cn.tolim.pudans.iam.communal.entity.setting.AdministratorEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.AdministratorRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.infra.context.ServletContextService;
import cn.tolim.pudans.iam.infra.security.userdetails.Group;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import cn.tolim.pudans.iam.portal.dto.PortalSessionView;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class PortalSessionService {

    private final GlobalSessionService globalSessionService;
    private final UserRepository userRepository;
    private final AdministratorRepository administratorRepository;
    private final UserGroupMemberRepository userGroupMemberRepository;
    private final UserGroupRepository userGroupRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectProvider<IamAuthProperties> authPropertiesProvider;

    public PortalSessionService(GlobalSessionService globalSessionService,
                                UserRepository userRepository,
                                AdministratorRepository administratorRepository,
                                UserGroupMemberRepository userGroupMemberRepository,
                                UserGroupRepository userGroupRepository,
                                OrganizationMemberRepository organizationMemberRepository,
                                OrganizationRepository organizationRepository,
                                ObjectProvider<IamAuthProperties> authPropertiesProvider) {
        this.globalSessionService = globalSessionService;
        this.userRepository = userRepository;
        this.administratorRepository = administratorRepository;
        this.userGroupMemberRepository = userGroupMemberRepository;
        this.userGroupRepository = userGroupRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationRepository = organizationRepository;
        this.authPropertiesProvider = authPropertiesProvider;
    }

    // Resolves the current portal identity from the global login session or the upstream authentication
    // Parameter request: HttpServletRequest current request
    // Returns: Optional<PortalIdentity>
    public Optional<PortalIdentity> resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(IamAuthTypes.SESSION_ATTRIBUTE);
        if (attribute instanceof LoginSessionEntity attributeSession) {
            if (!attributeSession.isActiveAt(LocalDateTime.now())) {
                return Optional.empty();
            }
            return fromLoginSession(attributeSession);
        }
        String sid = resolveSessionId(request);
        if (StringUtils.isNotBlank(sid)) {
            return globalSessionService.findActive(sid.trim()).flatMap(this::fromLoginSession);
        }
        return fromSecurityContext();
    }

    private Optional<PortalIdentity> fromLoginSession(LoginSessionEntity session) {
        PortalSessionView sessionView = new PortalSessionView();
        sessionView.setActive(Boolean.TRUE);
        sessionView.setCreatedAt(session.getCreatedAt());
        sessionView.setLastSeenAt(session.getLastSeenAt());
        sessionView.setExpiresAt(session.getExpiresAt());
        sessionView.setLoginIp(session.getIp());
        sessionView.setUserAgent(session.getUserAgent());
        if (IamAuthTypes.isAdmin(session.getUserType())) {
            return administratorRepository.findById(session.getUserId())
                    .filter(administrator -> !Boolean.TRUE.equals(administrator.isDisabled()))
                    .map(administrator -> fromAdministrator(administrator, session.getOrgId(), sessionView));
        }
        if (StringUtils.isBlank(session.getUserId())) {
            return Optional.empty();
        }
        return userRepository.findById(session.getUserId())
                .filter(user -> !isUnavailable(user))
                .map(user -> fromUser(user, session.getOrgId(), sessionView, List.of()));
    }

    private Optional<PortalIdentity> fromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!SecurityUtils.isPrincipalAuthenticated(authentication)) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return fromUserDetails(userDetails);
        }
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return fromAuthenticatedUser(authenticatedUser);
        }
        return Optional.empty();
    }

    private Optional<PortalIdentity> fromUserDetails(UserDetails userDetails) {
        if (StringUtils.isBlank(userDetails.getId())) {
            return Optional.empty();
        }
        if (UserType.ADMIN.equals(userDetails.getUserType())) {
            return administratorRepository.findById(userDetails.getId())
                    .filter(administrator -> !Boolean.TRUE.equals(administrator.isDisabled()))
                    .map(administrator -> fromAdministrator(administrator, null, null));
        }
        List<String> principalGroupIds = userDetails.getGroups() == null ? List.of()
                : userDetails.getGroups().stream()
                .map(Group::getId)
                .filter(StringUtils::isNotBlank)
                .toList();
        Optional<UserEntity> optional = userRepository.findById(userDetails.getId());
        if (optional.isPresent()) {
            UserEntity user = optional.get();
            if (isUnavailable(user)) {
                return Optional.empty();
            }
            return Optional.of(fromUser(user, null, null, principalGroupIds));
        }
        List<String> groups = resolveGroupCodes(principalGroupIds);
        PortalIdentity identity = new PortalIdentity(userDetails.getId(), userDetails.getUsername(),
                displayName(userDetails.getFullName(), userDetails.getNickName(), userDetails.getUsername()),
                IamAuthTypes.of(userDetails.getUserType()), null, null, userDetails.getEmail(),
                userDetails.getPhone(), userDetails.getAvatar(), userDetails.getEmailVerified(),
                userDetails.getPhoneVerified(), groups, null);
        return Optional.of(identity);
    }

    private Optional<PortalIdentity> fromAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        String userId = authenticatedUser.userId();
        if (StringUtils.isBlank(userId)) {
            return Optional.empty();
        }
        String organizationId = authenticatedUser.organizationId();
        Optional<UserEntity> optional = userRepository.findById(userId);
        if (optional.isPresent()) {
            UserEntity user = optional.get();
            if (isUnavailable(user)) {
                return Optional.empty();
            }
            return Optional.of(fromUser(user, organizationId, null, List.of()));
        }
        String organizationName = resolveOrganizationName(organizationId);
        PortalIdentity identity = new PortalIdentity(userId, authenticatedUser.username(),
                displayName(authenticatedUser.displayName(), null, authenticatedUser.username()),
                IamAuthTypes.USER, organizationId, organizationName, null, null, null, null, null,
                List.of(), null);
        return Optional.of(identity);
    }

    private PortalIdentity fromUser(UserEntity user, String preferredOrgId, PortalSessionView sessionView,
                                    Collection<String> extraGroupIds) {
        String userId = user.getId();
        List<String> groupIds = new ArrayList<>();
        for (UserGroupMemberEntity member : userGroupMemberRepository.findByUserId(userId)) {
            addIfPresent(groupIds, member.getGroupId());
        }
        if (extraGroupIds != null) {
            extraGroupIds.forEach(groupId -> addIfPresent(groupIds, groupId));
        }
        List<String> groups = resolveGroupCodes(groupIds);
        String organizationId = preferredOrgId;
        if (StringUtils.isBlank(organizationId)) {
            for (OrganizationMemberEntity member : organizationMemberRepository.findAllByUserId(userId)) {
                if (StringUtils.isNotBlank(member.getOrgId())) {
                    organizationId = member.getOrgId();
                    break;
                }
            }
        }
        String organizationName = resolveOrganizationName(organizationId);
        return new PortalIdentity(userId, user.getUsername(),
                displayName(user.getFullName(), user.getNickName(), user.getUsername()), IamAuthTypes.USER,
                organizationId, organizationName, user.getEmail(), user.getPhone(), user.getAvatar(),
                user.getEmailVerified(), user.getPhoneVerified(), groups, sessionView);
    }

    private PortalIdentity fromAdministrator(AdministratorEntity administrator, String organizationId,
                                             PortalSessionView sessionView) {
        String organizationName = resolveOrganizationName(organizationId);
        return new PortalIdentity(administrator.getId(), administrator.getUsername(),
                displayName(administrator.getFullName(), administrator.getNickName(),
                        administrator.getUsername()), IamAuthTypes.ADMIN, organizationId, organizationName,
                administrator.getEmail(), administrator.getPhone(), administrator.getAvatar(),
                administrator.getEmailVerified(), administrator.getPhoneVerified(), List.of(),
                sessionView);
    }

    private String resolveSessionId(HttpServletRequest request) {
        Object attribute = request.getAttribute(IamAuthTypes.SESSION_ATTRIBUTE);
        if (attribute instanceof String value && StringUtils.isNotBlank(value)) {
            return value.trim();
        }
        String header = request.getHeader(IamAuthTypes.SESSION_HEADER);
        if (StringUtils.isNotBlank(header)) {
            return header.trim();
        }
        Cookie cookie = ServletContextService.readCookieByName(request, sessionCookieName());
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())) {
            return cookie.getValue().trim();
        }
        return null;
    }

    private String sessionCookieName() {
        IamAuthProperties properties = authPropertiesProvider.getIfAvailable();
        if (properties != null && properties.getCookie() != null
                && StringUtils.isNotBlank(properties.getCookie().getName())) {
            return properties.getCookie().getName();
        }
        return PortalConstants.DEFAULT_SESSION_COOKIE_NAME;
    }

    private List<String> resolveGroupCodes(Collection<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        return userGroupRepository.findAllById(groupIds).stream()
                .map(UserGroupEntity::getCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private String resolveOrganizationName(String organizationId) {
        if (StringUtils.isBlank(organizationId)) {
            return null;
        }
        return organizationRepository.findById(organizationId)
                .map(OrganizationEntity::getName)
                .orElse(null);
    }

    private static boolean isUnavailable(UserEntity user) {
        if (UserStatus.DISABLED.equals(user.getStatus())) {
            return true;
        }
        return user.getExpireDate() != null && user.getExpireDate().isBefore(LocalDate.now());
    }

    private static void addIfPresent(Collection<String> values, String value) {
        if (StringUtils.isNotBlank(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first;
        }
        return StringUtils.isNotBlank(second) ? second : null;
    }

    private static String displayName(String fullName, String nickName, String username) {
        if (StringUtils.isNotBlank(fullName)) {
            return fullName;
        }
        if (StringUtils.isNotBlank(nickName)) {
            return nickName;
        }
        return username;
    }
}