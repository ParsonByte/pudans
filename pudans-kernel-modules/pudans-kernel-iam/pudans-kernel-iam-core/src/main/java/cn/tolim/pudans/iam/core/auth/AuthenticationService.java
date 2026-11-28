package cn.tolim.pudans.iam.core.auth;

import cn.tolim.pudans.contract.iam.dto.auth.LoginConfigResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutRequest;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeRequest;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaVerifyRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.setting.AdministratorEntity;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.AdministratorRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditService;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditType;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.mfa.MfaAuthenticator;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallenge;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallengeService;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaChallengeInvalidException;
import cn.tolim.pudans.iam.core.auth.mfa.exception.MfaVerificationException;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService.SessionRevocationResult;
import cn.tolim.pudans.iam.core.auth.ticket.TicketTargetValidator;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthenticationService {

    private final UserAccountService userAccountService;
    private final ApplicationAccessService applicationAccessService;
    private final TicketTargetValidator ticketTargetValidator;
    private final GlobalSessionService globalSessionService;
    private final AuthAuditService authAuditService;
    private final UserRepository userRepository;
    private final AdministratorRepository administratorRepository;
    private final MfaChallengeService mfaChallengeService;
    private final ObjectProvider<MfaAuthenticator> mfaAuthenticatorProvider;

    public AuthenticationService(UserAccountService userAccountService,
                                 ApplicationAccessService applicationAccessService,
                                 TicketTargetValidator ticketTargetValidator,
                                 GlobalSessionService globalSessionService,
                                 AuthAuditService authAuditService,
                                 UserRepository userRepository,
                                 AdministratorRepository administratorRepository,
                                 MfaChallengeService mfaChallengeService,
                                 ObjectProvider<MfaAuthenticator> mfaAuthenticatorProvider) {
        this.userAccountService = userAccountService;
        this.applicationAccessService = applicationAccessService;
        this.ticketTargetValidator = ticketTargetValidator;
        this.globalSessionService = globalSessionService;
        this.authAuditService = authAuditService;
        this.userRepository = userRepository;
        this.administratorRepository = administratorRepository;
        this.mfaChallengeService = mfaChallengeService;
        this.mfaAuthenticatorProvider = mfaAuthenticatorProvider;
    }

    // Authenticates a password login and either creates the global login session directly or
    // returns a short-lived MFA challenge when the account has a second factor enrolled
    // Parameter request: LoginRequest
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LoginResponse
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidAuthRequestException("login_request_invalid",
                    "Username and password must not be blank");
        }
        String username = request.getUsername().trim();
        AuthenticatedAccount account;
        try {
            account = userAccountService.authenticate(username, request.getPassword());
        } catch (AuthenticationFailedException exception) {
            authAuditService.record(AuthAuditType.LOGIN_FAILURE, null, null, request.getAppCode(), null,
                    ip, userAgent, false, exception.getMessage());
            throw exception;
        }
        return completeLogin(account, request.getAppCode(), request.getTargetUrl(), ip, userAgent);
    }

    // Completes a first-factor authentication with application access, MFA and session creation
    // Parameter account: AuthenticatedAccount
    // Parameter appCodeValue: String
    // Parameter targetUrl: String
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LoginResponse
    public LoginResponse completeLogin(AuthenticatedAccount account, String appCodeValue,
                                       String targetUrl, String ip, String userAgent) {
        String appCode = normalize(appCodeValue);
        String resolvedTarget = null;
        if (appCode != null) {
            AppEntity app = applicationAccessService.requireTicketReadyApp(appCode);
            if (!applicationAccessService.canAccess(account.userId(), account.orgId(),
                    account.userType(), app)) {
                authAuditService.record(AuthAuditType.LOGIN_FAILURE, account.userId(),
                        account.userType(), app.getCode(), null, ip, userAgent, false,
                        "app_access_denied");
                throw new cn.tolim.pudans.iam.core.auth.exception.AppAccessDeniedException(app.getCode());
            }
            resolvedTarget = ticketTargetValidator.validate(app, targetUrl);
        }
        String redirectUrl = resolvedTarget != null ? resolvedTarget : targetUrl;
        Set<MfaMethod> mfaMethods = availableMfaMethods(account.userId(), account.userType());
        if (!mfaMethods.isEmpty()) {
            MfaChallenge challenge = mfaChallengeService.create(account.userId(), account.userType(),
                    account.orgId(), account.username(), appCode, redirectUrl, mfaMethods);
            authAuditService.record(AuthAuditType.MFA_CHALLENGE_ISSUED, account.userId(),
                    account.userType(), appCode, null, ip, userAgent, true,
                    challenge.methods().stream().map(Enum::name).collect(Collectors.joining(",")));
            return mfaChallengeResponse(challenge);
        }
        LoginSessionEntity session = globalSessionService.createSession(account.userId(),
                account.userType(), account.orgId(), ip, userAgent);
        updateAuthenticationStatistics(account.userId(), account.userType(), ip);
        UserInfo userInfo = userAccountService.loadUserInfo(session);
        authAuditService.record(AuthAuditType.LOGIN_SUCCESS, account.userId(), account.userType(),
                appCode, session.getSid(), ip, userAgent, true, null);
        return sessionResponse(session, userInfo, appCode, redirectUrl);
    }

    // Returns the pending MFA challenge metadata for a username and optional application
    // Parameter request: MfaChallengeRequest
    // Returns: MfaChallengeResponse
    public MfaChallengeResponse mfaChallenge(MfaChallengeRequest request) {
        if (request == null || request.getUsername() == null || request.getUsername().isBlank()) {
            throw new InvalidAuthRequestException("mfa_challenge_request_invalid",
                    "Username must not be blank");
        }
        String appCode = normalize(request.getAppCode());
        MfaChallenge challenge = mfaChallengeService.findLatest(request.getUsername().trim(), appCode)
                .orElseThrow(() -> new MfaChallengeInvalidException(
                        "No pending MFA challenge for this account"));
        return challengeMetadata(challenge);
    }

    // Verifies a TOTP code for a pending challenge and completes the global login session
    // Parameter request: MfaVerifyRequest
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LoginResponse
    public LoginResponse verifyMfa(MfaVerifyRequest request, String ip, String userAgent) {
        if (request == null || request.getChallengeId() == null || request.getChallengeId().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            throw new InvalidAuthRequestException("mfa_verify_request_invalid",
                    "Challenge id and verification code must not be blank");
        }
        MfaChallenge challenge = requireMfaChallenge(request.getChallengeId(),
                request.getUsername(), request.getAppCode(), MfaMethod.TOTP);
        MfaAuthenticator authenticator = mfaAuthenticatorProvider.getIfAvailable();
        if (authenticator == null) {
            throw new InvalidAuthRequestException("mfa_not_available",
                    "No MFA authenticator is configured");
        }
        boolean valid;
        try {
            valid = authenticator.verifyTotp(challenge.userId(), challenge.userType(),
                    request.getCode().trim());
        } catch (RuntimeException exception) {
            auditMfaFailure(challenge, ip, userAgent, exception.getMessage());
            throw exception;
        }
        if (!valid) {
            auditMfaFailure(challenge, ip, userAgent, "mfa_code_invalid");
            throw new MfaVerificationException("The one-time code is invalid");
        }
        return completeMfaLogin(challenge.challengeId(), ip, userAgent);
    }

    // Resolves and validates a pending MFA challenge for a second factor ceremony
    // Parameter challengeId: String
    // Parameter username: String
    // Parameter appCode: String
    // Parameter requiredMethod: MfaMethod
    // Returns: MfaChallenge
    public MfaChallenge requireMfaChallenge(String challengeId, String username, String appCode,
                                            MfaMethod requiredMethod) {
        MfaChallenge challenge = mfaChallengeService.require(challengeId);
        if (username != null && !username.isBlank()
                && !challenge.username().equals(username.trim())) {
            throw new MfaChallengeInvalidException("MFA challenge does not belong to this account");
        }
        String normalizedAppCode = normalize(appCode);
        if (normalizedAppCode != null && !normalizedAppCode.equals(challenge.appCode())) {
            throw new MfaChallengeInvalidException("MFA challenge does not belong to this application");
        }
        if (requiredMethod != null && !challenge.supports(requiredMethod)) {
            throw new InvalidAuthRequestException("mfa_method_unavailable",
                    "The challenge does not allow method " + requiredMethod.name());
        }
        return challenge;
    }

    // Consumes a verified MFA challenge and creates the global login session
    // Parameter challengeId: String
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LoginResponse
    public LoginResponse completeMfaLogin(String challengeId, String ip, String userAgent) {
        MfaChallenge challenge = mfaChallengeService.consume(challengeId);
        LoginSessionEntity session = globalSessionService.createSession(challenge.userId(),
                challenge.userType(), challenge.orgId(), ip, userAgent);
        updateAuthenticationStatistics(challenge.userId(), challenge.userType(), ip);
        UserInfo userInfo = userAccountService.loadUserInfo(session);
        authAuditService.record(AuthAuditType.MFA_VERIFICATION_SUCCESS, challenge.userId(),
                challenge.userType(), challenge.appCode(), session.getSid(), ip, userAgent, true,
                null);
        return sessionResponse(session, userInfo, challenge.appCode(), challenge.targetUrl());
    }

    // Records a failed second factor verification without leaking the challenge details
    // Parameter challenge: MfaChallenge
    // Parameter ip: String
    // Parameter userAgent: String
    // Parameter reason: String
    public void auditMfaFailure(MfaChallenge challenge, String ip, String userAgent, String reason) {
        if (challenge == null) {
            return;
        }
        authAuditService.record(AuthAuditType.MFA_VERIFICATION_FAILURE, challenge.userId(),
                challenge.userType(), challenge.appCode(), null, ip, userAgent, false, reason);
    }

    // Resolves the current user information from a global login session
    // Parameter sid: String
    // Returns: UserInfo
    public UserInfo currentUser(String sid) {
        LoginSessionEntity session = globalSessionService.requireActive(sid);
        return userAccountService.loadUserInfo(session);
    }

    // Resolves the active global login session
    // Parameter sid: String
    // Returns: LoginSessionEntity
    public LoginSessionEntity currentSession(String sid) {
        return globalSessionService.requireActive(sid);
    }

    // Logs out the given global login session or every session of a user
    // Parameter request: LogoutRequest
    // Parameter cookieSid: String
    // Parameter ip: String
    // Parameter userAgent: String
    // Returns: LogoutResponse
    public LogoutResponse logout(LogoutRequest request, String cookieSid, String ip, String userAgent) {
        String sid = resolveSid(request == null ? null : request.getSid(), cookieSid);
        String userId = request == null ? null : request.getUserId();
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
                ? "user_logout" : request.getReason();
        SessionRevocationResult result;
        if (userId != null && !userId.isBlank()) {
            result = globalSessionService.revokeAllForUser(userId, reason);
        } else if (sid != null && !sid.isBlank()) {
            result = globalSessionService.revoke(sid, reason);
        } else {
            return logoutResponse(0, 0);
        }
        authAuditService.record(AuthAuditType.LOGOUT, result.userId(), null, null, result.sid(), ip,
                userAgent, true, reason);
        return logoutResponse(result.revokedSessionCount(), result.notifiedApplicationCount());
    }

    // Builds the public login page configuration
    // Parameter appCode: String
    // Returns: LoginConfigResponse
    public LoginConfigResponse loginConfig(String appCode) {
        return LoginConfigResponse.builder()
                .appCode(appCode)
                .passwordLoginEnabled(Boolean.TRUE)
                .captchaEnabled(Boolean.FALSE)
                .mfaEnabled(Boolean.FALSE)
                .identityProviders(List.of())
                .build();
    }

    private Set<MfaMethod> availableMfaMethods(String userId, String userType) {
        MfaAuthenticator authenticator = mfaAuthenticatorProvider.getIfAvailable();
        if (authenticator == null) {
            return Set.of();
        }
        Set<MfaMethod> methods = authenticator.availableMethods(userId, userType);
        return methods == null ? Set.of() : methods;
    }

    private void updateAuthenticationStatistics(String userId, String userType, String ip) {
        LocalDateTime now = LocalDateTime.now();
        if (IamAuthTypes.isAdmin(userType)) {
            administratorRepository.updateAuthSucceedInfo(userId, ip, now);
        } else {
            userRepository.updateAuthSucceedInfo(userId, ip, now);
        }
    }

    private LoginResponse mfaChallengeResponse(MfaChallenge challenge) {
        LoginResponse response = new LoginResponse();
        response.setMfaRequired(Boolean.TRUE);
        response.setMfaChallengeId(challenge.challengeId());
        response.setAppCode(challenge.appCode());
        response.setRedirectUrl(challenge.targetUrl());
        return response;
    }

    private MfaChallengeResponse challengeMetadata(MfaChallenge challenge) {
        return MfaChallengeResponse.builder()
                .challengeId(challenge.challengeId())
                .username(challenge.username())
                .method(challenge.preferredMethod().name())
                .expiresAt(LocalDateTime.ofInstant(challenge.expiresAt(), ZoneOffset.UTC))
                .build();
    }

    private LoginResponse sessionResponse(LoginSessionEntity session, UserInfo userInfo,
                                          String appCode, String redirectUrl) {
        LoginResponse response = new LoginResponse();
        response.setSid(session.getSid());
        response.setUser(userInfo);
        response.setExpiresAt(session.getExpiresAt());
        response.setMfaRequired(Boolean.FALSE);
        response.setAppCode(appCode);
        response.setRedirectUrl(redirectUrl);
        return response;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveSid(String requestSid, String cookieSid) {
        if (requestSid != null && !requestSid.isBlank()) {
            return requestSid;
        }
        return cookieSid;
    }

    private LogoutResponse logoutResponse(int revokedSessions, int notifiedApplications) {
        LogoutResponse response = new LogoutResponse();
        response.setSuccess(Boolean.TRUE);
        response.setRevokedSessionCount(revokedSessions);
        response.setNotifiedApplicationCount(notifiedApplications);
        response.setRevokedAt(LocalDateTime.now());
        return response;
    }
}