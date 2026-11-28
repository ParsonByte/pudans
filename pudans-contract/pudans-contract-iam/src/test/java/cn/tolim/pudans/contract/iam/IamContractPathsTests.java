package cn.tolim.pudans.contract.iam;

import static cn.tolim.pudans.contract.iam.IamContractPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class IamContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(AUTH_LOGIN).startsWith(BASE);
        assertThat(AUTH_LOGOUT).startsWith(BASE);
        assertThat(AUTH_LOGIN_CONFIG).startsWith(BASE);
        assertThat(AUTH_MFA_CHALLENGE).startsWith(BASE);
        assertThat(AUTH_MFA_VERIFY).startsWith(BASE);
        assertThat(AUTH_FORGOT_PASSWORD).startsWith(BASE);
        assertThat(AUTH_RESET_PASSWORD).startsWith(BASE);
        assertThat(SESSION_VALIDATE).startsWith(BASE);
        assertThat(SESSION_VALIDATE_ALIAS).startsWith(BASE);
        assertThat(SESSION_TOUCH).startsWith(BASE);
        assertThat(SESSION_REVOKE).startsWith(BASE);
        assertThat(SESSION_REVOKE_USER).startsWith(BASE);
        assertThat(SESSION_DETAIL).startsWith(BASE);
        assertThat(SESSION_USER).startsWith(BASE);
        assertThat(TICKET_ISSUE).startsWith(BASE);
        assertThat(TICKET_EXCHANGE).startsWith(BASE);
        assertThat(TICKET_ISSUE_ALIAS).startsWith(BASE);
        assertThat(TICKET_EXCHANGE_ALIAS).startsWith(BASE);
        assertThat(AUTHZ_EVALUATE).startsWith(BASE);
        assertThat(AUTHZ_EVALUATE_BATCH).startsWith(BASE);
        assertThat(AUTHZ_SIMULATE).startsWith(BASE);
        assertThat(AUTHZ_EFFECTIVE_PERMISSIONS).startsWith(BASE);
        assertThat(AUTHZ_LOGS).startsWith(BASE);
        assertThat(AUTHZ_ACTIONS).startsWith(BASE);
        assertThat(AUTHZ_RESOURCE_TYPES).startsWith(BASE);
        assertThat(USER).startsWith(BASE);
        assertThat(USER_DETAIL).startsWith(BASE);
        assertThat(USER_BY_USERNAME).startsWith(BASE);
        assertThat(USER_BATCH).startsWith(BASE);
        assertThat(USER_ROLES).startsWith(BASE);
        assertThat(ME).startsWith(BASE);
        assertThat(ME_APPS).startsWith(BASE);
        assertThat(APP).startsWith(BASE);
        assertThat(APP_DETAIL).startsWith(BASE);
        assertThat(APP_ACCESS).startsWith(BASE);
        assertThat(APP_REGISTER).startsWith(BASE);
        assertThat(APP_ACTIONS).startsWith(BASE);
        assertThat(APP_RESOURCE_TYPES).startsWith(BASE);
    }
}