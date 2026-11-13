import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const repo = new URL("../../", import.meta.url).pathname;
const authClient = readFileSync(
  repo + "web/packages/api-client/src/client/auth-api.ts",
  "utf8",
);
const publicEndpoint = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/security/authentication/login/LoginPublicEndpoint.java",
  "utf8",
);
const publicKey = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/security/authentication/login/PublicKeyRouteBuilder.java",
  "utf8",
);
const recovery = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/security/authentication/recovery/PasswordRecoveryEndpoint.java",
  "utf8",
);
const authorization = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/security/authorization/AuthorizationExchangeConfigurers.java",
  "utf8",
);
const spa = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/infra/webfilter/SpaIndexWebFilter.java",
  "utf8",
);
const sessionService = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/core/rbac/service/UiPermissionService.java",
  "utf8",
);
const role = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/contract/core/extension/rbac/Role.java",
  "utf8",
);

test("api-client auth routes match backend handlers", () => {
  assert.ok(authClient.includes('AUTH_API_BASE = "/apis/auth.api.pudans.tolim.cn/v1alpha1"'));
  for (const route of ["login/status", "login/providers", "login/session"]) {
    assert.ok(authClient.includes(route), route);
    assert.ok(publicEndpoint.includes(route), route);
  }
  assert.ok(authClient.includes("/login/public-key"));
  assert.ok(publicKey.includes("/login/public-key"));
  assert.ok(recovery.includes("/send-code"));
  assert.ok(recovery.includes("/reset-password"));
});

test("auth, session, provider and status routes are public", () => {
  assert.ok(authorization.includes('"/apis/" + ApiGroups.AUTH + "/v1alpha1/login/**"'));
});

test("one SPA entry serves auth, setup, console, UC and forbidden", () => {
  assert.ok(spa.includes('"/index.html"'));
  for (const root of ["/auth", "/console", "/uc", "/setup", "/forbidden"]) {
    assert.ok(spa.includes(root), root);
  }
});

test("console disallow annotation is exposed through the auth session", () => {
  assert.ok(role.includes("DISALLOW_ACCESS_CONSOLE_ANNO"));
  assert.ok(sessionService.includes("isConsoleAccessDisallowed"));
  assert.ok(publicEndpoint.includes("consoleAccessDisallowed"));
  assert.ok(publicEndpoint.includes("uiPermissions"));
});

test("post-login landing defaults to the console", () => {
  const properties = readFileSync(
    repo + "appkernel/src/main/java/cn/tolim/pudans/infra/properties/SecurityProperties.java",
    "utf8",
  );
  assert.ok(properties.includes('private String postLoginUrl = "/console"'));
});

test("recovery failures expose stable machine codes", () => {
  const service = readFileSync(
    repo + "appkernel/src/main/java/cn/tolim/pudans/security/authentication/recovery/PasswordRecoveryService.java",
    "utf8",
  );
  const mailer = readFileSync(
    repo + "appkernel/src/main/java/cn/tolim/pudans/security/authentication/recovery/PasswordResetMailer.java",
    "utf8",
  );
  for (const code of ["recovery-code-invalid", "recovery-code-cooldown"]) {
    assert.ok(service.includes(code), code);
  }
  assert.ok(mailer.includes("recovery-mail-unavailable"));
  assert.ok(service.includes("recovery-password-invalid"));
});
