import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const repo = new URL("../../", import.meta.url).pathname;
const apiClientEntry = readFileSync(repo + "web/packages/api-client/entry/api-client.ts", "utf8");
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
const webFlux = readFileSync(
  repo + "appkernel/src/main/java/cn/tolim/pudans/infra/config/WebFluxConfig.java",
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

test("api-client entry matches backend auth handlers", () => {
  assert.ok(apiClientEntry.includes('"/apis/auth.api.pudans.tolim.cn/v1alpha1"'));
  for (const route of ["login/status", "login/providers", "login/session"]) {
    assert.ok(apiClientEntry.includes(route), route);
    assert.ok(publicEndpoint.includes(route), route);
  }
  assert.ok(apiClientEntry.includes("/login/public-key"));
  assert.ok(publicKey.includes("/login/public-key"));
  assert.ok(recovery.includes("/send-code"));
  assert.ok(recovery.includes("/reset-password"));
  assert.ok(apiClientEntry.includes("/apis/public.api.pudans.tolim.cn/v1alpha1"));
});

test("auth, session, provider and status routes are public", () => {
  assert.ok(authorization.includes('"/apis/" + ApiGroups.AUTH + "/v1alpha1/login/**"'));
});

test("the backend maps auth, console and uc roots to separate HTML entries", () => {
  assert.ok(spa.includes('AUTH_ENTRY = "/auth.html"'));
  assert.ok(spa.includes('CONSOLE_ENTRY = "/console.html"'));
  assert.ok(spa.includes('UC_ENTRY = "/uc.html"'));
  for (const root of ["/auth", "/console", "/uc", "/setup", "/forbidden"]) {
    assert.ok(spa.includes(root), root);
  }
  assert.ok(webFlux.includes('"/ui-assets/**"'));
  assert.ok(webFlux.includes('classpath:/static/assets/'));
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
