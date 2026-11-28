package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse;
import cn.tolim.pudans.contract.iam.dto.session.ApplicationSessionSummary;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionRevokeUserResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionSummary;
import cn.tolim.pudans.contract.iam.dto.session.SessionTouchResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateResponse;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.communal.session.AppSessionRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditService;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditType;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SessionManagementService {

    private final GlobalSessionService globalSessionService;
    private final AppRepository appRepository;
    private final AppSessionRepository appSessionRepository;
    private final BackchannelLogoutNotifier backchannelLogoutNotifier;
    private final AuthAuditService authAuditService;
    private final UserAccountService userAccountService;
    private final ApplicationAccessService applicationAccessService;

    public SessionManagementService(GlobalSessionService globalSessionService,
                                    AppRepository appRepository,
                                    AppSessionRepository appSessionRepository,
                                    BackchannelLogoutNotifier backchannelLogoutNotifier,
                                    AuthAuditService authAuditService,
                                    UserAccountService userAccountService,
                                    ApplicationAccessService applicationAccessService) {
        this.globalSessionService = globalSessionService;
        this.appRepository = appRepository;
        this.appSessionRepository = appSessionRepository;
        this.backchannelLogoutNotifier = backchannelLogoutNotifier;
        this.authAuditService = authAuditService;
        this.userAccountService = userAccountService;
        this.applicationAccessService = applicationAccessService;
    }

    // Validates a global login session for an application or the gateway
    // Parameter request: SessionValidateRequest
    // Parameter ip: String
    // Returns: SessionValidateResponse
    public SessionValidateResponse validate(SessionValidateRequest request, String ip) {
        if (request == null || request.getSid() == null || request.getSid().isBlank()) {
            return SessionValidateResponse.builder().active(Boolean.FALSE).reason("sid_missing").build();
        }
        Optional<LoginSessionEntity> optional = globalSessionService.findActive(request.getSid());
        if (optional.isEmpty()) {
            authAuditService.record(AuthAuditType.SESSION_VALIDATION_REJECTED, null, null,
                    request.getAppCode(), request.getSid(), ip, null, false, "session_invalid_or_expired");
            return SessionValidateResponse.builder().active(Boolean.FALSE)
                    .appCode(request.getAppCode()).reason("session_invalid_or_expired").build();
        }
        LoginSessionEntity session = optional.get();
        if (!userAccountService.isAccountUsable(session.getUserId(), session.getUserType())) {
            authAuditService.record(AuthAuditType.SESSION_VALIDATION_REJECTED, session.getUserId(),
                    session.getUserType(), request.getAppCode(), request.getSid(), ip, null, false,
                    "account_disabled");
            return SessionValidateResponse.builder().active(Boolean.FALSE)
                    .appCode(request.getAppCode()).reason("account_disabled").build();
        }
        String appCode = request.getAppCode();
        if (appCode != null && !appCode.isBlank()) {
            Optional<AppEntity> app = appRepository.findByCode(appCode);
            if (app.isEmpty()) {
                return SessionValidateResponse.builder().active(Boolean.FALSE).appCode(appCode)
                        .reason("app_not_found").build();
            }
            if (!Boolean.TRUE.equals(app.get().getEnabled())) {
                return SessionValidateResponse.builder().active(Boolean.FALSE).appCode(appCode)
                        .reason("app_disabled").build();
            }
            if (!applicationAccessService.canAccess(session.getUserId(), session.getOrgId(),
                    session.getUserType(), app.get())) {
                return SessionValidateResponse.builder().active(Boolean.FALSE).appCode(appCode)
                        .reason("app_access_denied").build();
            }
        }
        return SessionValidateResponse.builder()
                .active(Boolean.TRUE)
                .sub(session.getUserId())
                .userType(session.getUserType())
                .orgId(session.getOrgId())
                .appCode(appCode)
                .expiresAt(session.getExpiresAt())
                .build();
    }

    // Touches a global login session and extends its last seen time
    // Parameter sid: String
    // Returns: SessionTouchResponse
    public SessionTouchResponse touch(String sid) {
        if (sid == null || sid.isBlank()) {
            return SessionTouchResponse.builder().active(Boolean.FALSE).reason("sid_missing").build();
        }
        Optional<LoginSessionEntity> optional = globalSessionService.findActive(sid);
        if (optional.isEmpty()) {
            return SessionTouchResponse.builder().active(Boolean.FALSE)
                    .reason("session_invalid_or_expired").build();
        }
        LoginSessionEntity session = optional.get();
        globalSessionService.touch(sid);
        LocalDateTime now = LocalDateTime.now();
        return SessionTouchResponse.builder()
                .active(Boolean.TRUE)
                .lastSeenAt(now)
                .expiresAt(session.getExpiresAt())
                .build();
    }

    // Revokes one global login session
    // Parameter request: SessionRevokeRequest
    // Parameter ip: String
    // Returns: SessionRevokeResponse
    public SessionRevokeResponse revoke(SessionRevokeRequest request) {
        if (request == null || request.getSid() == null || request.getSid().isBlank()) {
            throw new InvalidAuthRequestException("sid_missing", "sid must not be blank");
        }
        GlobalSessionService.SessionRevocationResult result = globalSessionService
                .revoke(request.getSid(), request.getReason());
        authAuditService.record(AuthAuditType.SESSION_REVOKED, result.userId(), null, null,
                request.getSid(), null, null, true, request.getReason());
        return SessionRevokeResponse.builder()
                .revoked(result.userId() != null)
                .sid(request.getSid())
                .reason(request.getReason())
                .revokedAt(result.revokedAt())
                .build();
    }

    // Revokes every global login session of a user
    // Parameter request: SessionRevokeUserRequest
    // Returns: SessionRevokeUserResponse
    public SessionRevokeUserResponse revokeUser(SessionRevokeUserRequest request) {
        if (request == null || request.getUserId() == null || request.getUserId().isBlank()) {
            throw new InvalidAuthRequestException("userId_missing", "userId must not be blank");
        }
        GlobalSessionService.SessionRevocationResult result = globalSessionService
                .revokeAllForUser(request.getUserId(), request.getReason());
        authAuditService.record(AuthAuditType.SESSION_REVOKED, request.getUserId(), null, null, null,
                null, null, true, request.getReason());
        return SessionRevokeUserResponse.builder()
                .userId(request.getUserId())
                .revokedCount(result.revokedSessionCount())
                .revokedAt(result.revokedAt())
                .build();
    }

    // Returns a global login session with its application session caches
    // Parameter sid: String
    // Returns: SessionSummary
    public SessionSummary getSession(String sid) {
        LoginSessionEntity session = globalSessionService.find(sid)
                .orElseThrow(() -> new InvalidAuthRequestException("session_not_found",
                        "Global login session [" + sid + "] does not exist"));
        return toSummary(session, sessionDetail(session));
    }

    // Lists every global login session of a user
    // Parameter userId: String
    // Returns: List
    public List<SessionSummary> listUserSessions(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidAuthRequestException("userId_missing", "userId must not be blank");
        }
        List<SessionSummary> summaries = new ArrayList<>();
        for (LoginSessionEntity session : globalSessionService.listUserSessions(userId)) {
            summaries.add(toSummary(session, sessionDetail(session)));
        }
        return summaries;
    }

    // Dispatches back-channel logout notifications for a global login session or a user
    // Parameter sid: String
    // Parameter userId: String
    // Parameter reason: String
    // Returns: LogoutResponse
    public LogoutResponse dispatchBackchannelLogout(String sid, String userId, String reason) {
        if ((sid == null || sid.isBlank()) && (userId == null || userId.isBlank())) {
            throw new InvalidAuthRequestException("sid_missing", "sid or userId must be provided");
        }
        String resolvedReason = reason == null || reason.isBlank() ? "backchannel_logout" : reason;
        List<AppSessionEntity> appSessions = new ArrayList<>();
        String resolvedUserId = userId;
        if (sid != null && !sid.isBlank()) {
            appSessions.addAll(appSessionRepository.findAllBySid(sid));
            if (resolvedUserId == null) {
                resolvedUserId = globalSessionService.find(sid).map(LoginSessionEntity::getUserId)
                        .orElse(null);
            }
        } else {
            for (LoginSessionEntity session : globalSessionService.listUserSessions(userId)) {
                appSessions.addAll(appSessionRepository.findAllBySid(session.getSid()));
            }
        }
        int notified = backchannelLogoutNotifier.notifyLogout(sid, resolvedUserId, resolvedReason,
                appSessions);
        LocalDateTime now = LocalDateTime.now();
        for (AppSessionEntity appSession : appSessions) {
            appSessionRepository.markBackchannelLogout(appSession.getAppCode(), appSession.getSid(), now);
        }
        authAuditService.record(AuthAuditType.BACKCHANNEL_LOGOUT, resolvedUserId, null, null, sid, null,
                null, true, resolvedReason);
        return LogoutResponse.builder()
                .success(Boolean.TRUE)
                .revokedSessionCount(0)
                .notifiedApplicationCount(notified)
                .revokedAt(now)
                .build();
    }

    // Requires an active global login session for management endpoints
    // Parameter sid: String
    // Returns: LoginSessionEntity
    public LoginSessionEntity requireActive(String sid) {
        return globalSessionService.findActive(sid).orElseThrow(SessionNotActiveException::new);
    }

    private List<AppSessionEntity> sessionDetail(LoginSessionEntity session) {
        return appSessionRepository.findAllBySid(session.getSid());
    }

    private SessionSummary toSummary(LoginSessionEntity session, List<AppSessionEntity> appSessions) {
        LocalDateTime now = LocalDateTime.now();
        List<ApplicationSessionSummary> applications = appSessions.stream().map(appSession ->
                ApplicationSessionSummary.builder()
                        .appId(appSession.getAppId())
                        .appCode(appSession.getAppCode())
                        .createdAt(appSession.getCreatedAt())
                        .lastSeenAt(appSession.getLastSeenAt())
                        .expiresAt(appSession.getExpiresAt())
                        .revoked(appSession.getRevoked())
                        .lastBackchannelAt(appSession.getLastBackchannelAt())
                        .active(appSession.isActiveAt(now))
                        .build()).toList();
        return SessionSummary.builder()
                .sid(session.getSid())
                .userId(session.getUserId())
                .userType(session.getUserType())
                .orgId(session.getOrgId())
                .createdAt(session.getCreatedAt())
                .lastSeenAt(session.getLastSeenAt())
                .expiresAt(session.getExpiresAt())
                .revoked(session.getRevoked())
                .revokeReason(session.getRevokeReason())
                .ip(session.getIp())
                .userAgent(session.getUserAgent())
                .active(session.isActiveAt(now))
                .applications(applications)
                .build();
    }
}