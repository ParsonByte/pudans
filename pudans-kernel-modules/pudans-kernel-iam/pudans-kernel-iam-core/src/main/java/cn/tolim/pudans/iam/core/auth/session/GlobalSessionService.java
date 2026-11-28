package cn.tolim.pudans.iam.core.auth.session;

import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.communal.session.AppSessionRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.communal.session.LoginSessionRepository;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.exception.SessionNotActiveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GlobalSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalSessionService.class);

    private final LoginSessionRepository loginSessionRepository;
    private final AppSessionRepository appSessionRepository;
    private final SessionCache sessionCache;
    private final BackchannelLogoutNotifier backchannelLogoutNotifier;
    private final SessionTokenGenerator tokenGenerator;
    private final IamAuthProperties properties;

    public GlobalSessionService(LoginSessionRepository loginSessionRepository,
                                AppSessionRepository appSessionRepository,
                                SessionCache sessionCache,
                                BackchannelLogoutNotifier backchannelLogoutNotifier,
                                SessionTokenGenerator tokenGenerator,
                                IamAuthProperties properties) {
        this.loginSessionRepository = loginSessionRepository;
        this.appSessionRepository = appSessionRepository;
        this.sessionCache = sessionCache;
        this.backchannelLogoutNotifier = backchannelLogoutNotifier;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
    }

    // Creates and persists a new global login session
    // Parameter userId: String
    // Parameter userType: String
    // Parameter orgId: String
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LoginSessionEntity
    @Transactional(rollbackFor = Exception.class)
    public LoginSessionEntity createSession(String userId, String userType, String orgId,
                                            String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(effectiveSessionTtl());
        LoginSessionEntity session = new LoginSessionEntity();
        session.setSid(tokenGenerator.generate());
        session.setUserId(userId);
        session.setUserType(userType);
        session.setOrgId(orgId);
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(expiresAt);
        session.setRevoked(Boolean.FALSE);
        session.setIp(truncate(ip, 50));
        session.setUserAgent(truncate(userAgent, 500));
        LoginSessionEntity saved = loginSessionRepository.save(session);
        sessionCache.put(saved.getSid(), Duration.between(now, expiresAt));
        LOGGER.info("Created global login session [{}] for user [{}] type [{}]", saved.getSid(),
                userId, userType);
        return saved;
    }

    // Finds a global login session by sid without checking its state
    // Parameter sid: String
    // Returns: Optional<LoginSessionEntity>
    public Optional<LoginSessionEntity> find(String sid) {
        if (sid == null || sid.isBlank()) {
            return Optional.empty();
        }
        return loginSessionRepository.findById(sid);
    }

    // Finds an active global login session by sid
    // Parameter sid: String
    // Returns: Optional<LoginSessionEntity>
    public Optional<LoginSessionEntity> findActive(String sid) {
        Optional<LoginSessionEntity> optional = find(sid);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        LoginSessionEntity session = optional.get();
        LocalDateTime now = LocalDateTime.now();
        if (!session.isActiveAt(now)) {
            sessionCache.evict(sid);
            return Optional.empty();
        }
        sessionCache.put(session.getSid(), Duration.between(now, session.getExpiresAt()));
        return Optional.of(session);
    }

    // Requires an active global login session, throws when it is missing, revoked or expired
    // Parameter sid: String
    // Returns: LoginSessionEntity
    public LoginSessionEntity requireActive(String sid) {
        return findActive(sid).orElseThrow(SessionNotActiveException::new);
    }

    // Alias kept for callers that use the validation vocabulary
    // Parameter sid: String
    // Returns: LoginSessionEntity
    public LoginSessionEntity validateActive(String sid) {
        return requireActive(sid);
    }

    // Updates the last seen time of an active global login session
    // Parameter sid: String
    public void touch(String sid) {
        findActive(sid).ifPresent(session -> loginSessionRepository.touch(sid, LocalDateTime.now()));
    }

    // Revokes one global login session and every application session bound to it
    // Parameter sid: String
    // Parameter reason: String
    // Returns: SessionRevocationResult
    @Transactional(rollbackFor = Exception.class)
    public SessionRevocationResult revoke(String sid, String reason) {
        Optional<LoginSessionEntity> optional = loginSessionRepository.findById(sid);
        String resolvedReason = reason == null || reason.isBlank() ? "user_logout" : reason;
        if (optional.isEmpty()) {
            return new SessionRevocationResult(sid, null, 0, 0, LocalDateTime.now());
        }
        LoginSessionEntity session = optional.get();
        int revoked = session.isActiveAt(LocalDateTime.now())
                ? loginSessionRepository.revokeBySid(sid, resolvedReason) : 0;
        List<AppSessionEntity> appSessions = appSessionRepository.findAllBySid(sid);
        appSessionRepository.revokeAllBySid(sid);
        sessionCache.evict(sid);
        int notified = dispatchAfterCommit(session.getSid(), session.getUserId(), resolvedReason, appSessions);
        return new SessionRevocationResult(sid, session.getUserId(), revoked, notified, LocalDateTime.now());
    }

    // Revokes every global login session of a user and all bound application sessions
    // Parameter userId: String
    // Parameter reason: String
    // Returns: SessionRevocationResult
    @Transactional(rollbackFor = Exception.class)
    public SessionRevocationResult revokeAllForUser(String userId, String reason) {
        String resolvedReason = reason == null || reason.isBlank() ? "user_logout" : reason;
        List<LoginSessionEntity> sessions = loginSessionRepository.findAllByUserIdAndRevokedFalse(userId);
        Map<String, List<AppSessionEntity>> appSessionsBySid = new LinkedHashMap<>();
        for (LoginSessionEntity session : sessions) {
            appSessionsBySid.put(session.getSid(), appSessionRepository.findAllBySid(session.getSid()));
        }
        int revoked = loginSessionRepository.revokeAllByUserId(userId, resolvedReason);
        int notified = 0;
        for (LoginSessionEntity session : sessions) {
            appSessionRepository.revokeAllBySid(session.getSid());
            sessionCache.evict(session.getSid());
            notified += dispatchAfterCommit(session.getSid(), userId, resolvedReason,
                    appSessionsBySid.getOrDefault(session.getSid(), List.of()));
        }
        return new SessionRevocationResult(null, userId, revoked, notified, LocalDateTime.now());
    }

    // Registers an application session cache entry bound to a global login session
    // Parameter sid: String
    // Parameter appId: String
    // Parameter appCode: String
    // Parameter userId: String
    // Parameter sessionExpiresAt: LocalDateTime
    // Returns: AppSessionEntity
    @Transactional(rollbackFor = Exception.class)
    public AppSessionEntity registerApplicationSession(String sid, String appId, String appCode,
                                                       String userId, LocalDateTime sessionExpiresAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime globalExpiresAt = now.plus(effectiveSessionTtl());
        LocalDateTime appExpiresAt = now.plus(effectiveAppSessionTtl());
        if (appExpiresAt.isAfter(globalExpiresAt)) {
            appExpiresAt = globalExpiresAt;
        }
        if (sessionExpiresAt != null && sessionExpiresAt.isBefore(appExpiresAt)) {
            appExpiresAt = sessionExpiresAt;
        }
        AppSessionEntity appSession = appSessionRepository.findByAppCodeAndSid(appCode, sid)
                .orElseGet(AppSessionEntity::new);
        if (appSession.getId() == null) {
            appSession.setSid(sid);
            appSession.setAppId(appId);
            appSession.setAppCode(appCode);
            appSession.setUserId(userId);
            appSession.setCreatedAt(now);
        }
        appSession.setLastSeenAt(now);
        appSession.setExpiresAt(appExpiresAt);
        appSession.setRevoked(Boolean.FALSE);
        return appSessionRepository.save(appSession);
    }

    // Finds an active application session cache
    // Parameter appCode: String
    // Parameter sid: String
    // Returns: Optional<AppSessionEntity>
    public Optional<AppSessionEntity> findActiveApplicationSession(String appCode, String sid) {
        return appSessionRepository.findActiveByAppCodeAndSid(appCode, sid, LocalDateTime.now());
    }

    // Deletes expired global login sessions and application session caches
    // Returns: int
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        int sessions = loginSessionRepository.deleteExpired(now);
        int appSessions = appSessionRepository.deleteExpired(now);
        LOGGER.debug("Cleaned up {} expired global login sessions and {} application sessions",
                sessions, appSessions);
        return sessions + appSessions;
    }

    // Lists every global login session of a user, newest first
    // Parameter userId: String
    // Returns: List
    public List<LoginSessionEntity> listUserSessions(String userId) {
        return loginSessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    // Lists every application session bound to a global login session
    // Parameter sid: String
    // Returns: List
    public List<AppSessionEntity> listApplicationSessions(String sid) {
        return appSessionRepository.findAllBySid(sid);
    }

    private int dispatchAfterCommit(String sid, String userId, String reason,
                                    List<AppSessionEntity> appSessions) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    backchannelLogoutNotifier.notifyLogout(sid, userId, reason, appSessions);
                }
            });
            return countNotifiable(appSessions);
        }
        return backchannelLogoutNotifier.notifyLogout(sid, userId, reason, appSessions);
    }

    private int countNotifiable(List<AppSessionEntity> appSessions) {
        if (appSessions == null || appSessions.isEmpty()) {
            return 0;
        }
        Map<String, String> logoutUrls = properties.getBackchannel().getAppUrls();
        return (int) appSessions.stream()
                .filter(appSession -> logoutUrls.containsKey(appSession.getAppCode()))
                .count();
    }

    private Duration effectiveSessionTtl() {
        Duration ttl = properties.getSession().getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(8) : ttl;
    }

    // Application session cache ttl clamped to the global login session ttl
    // Returns: Duration
    private Duration effectiveAppSessionTtl() {
        Duration globalTtl = effectiveSessionTtl();
        Duration ttl = properties.getSession().getAppSessionTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return globalTtl;
        }
        return ttl.compareTo(globalTtl) > 0 ? globalTtl : ttl;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // Result of a global session revocation
    public record SessionRevocationResult(String sid, String userId, int revokedSessionCount,
                                          int notifiedApplicationCount, LocalDateTime revokedAt) {
    }
}