package cn.tolim.pudans.iam.core.auth.audit;

public enum AuthAuditType {

    // Password login succeeded
    LOGIN_SUCCESS,

    // Password login failed
    LOGIN_FAILURE,

    // A second factor challenge was issued after password authentication
    MFA_CHALLENGE_ISSUED,

    // Second factor verification succeeded and the global session was created
    MFA_VERIFICATION_SUCCESS,

    // Second factor verification failed
    MFA_VERIFICATION_FAILURE,

    // Global logout completed
    LOGOUT,

    // A global login session was revoked by an administrator or an internal caller
    SESSION_REVOKED,

    // An SSO ticket was issued
    TICKET_ISSUED,

    // An SSO ticket was exchanged by an application
    TICKET_EXCHANGED,

    // A back-channel logout notification was dispatched
    BACKCHANNEL_LOGOUT,

    // A session validation was rejected
    SESSION_VALIDATION_REJECTED
}