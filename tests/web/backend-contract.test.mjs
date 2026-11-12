import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const repo = new URL("../../", import.meta.url).pathname;
const apiConstants = readFileSync(repo + "web/src/constants/api.ts", "utf8");
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

test("frontend auth API constants match the backend routes", () => {
  assert.match(apiConstants, /AUTH_API_BASE = "\/apis\/auth\.api\.pudans\.tolim\.cn\/v1alpha1"/);
  for (const route of ["login/session", "login/providers", "login/status"]) {
    assert.ok(publicEndpoint.includes(route), route);
  }
  assert.ok(recovery.includes("/send-code"));
  assert.ok(recovery.includes("/reset-password"));
  assert.ok(apiConstants.includes("recovery/send-code"));
  assert.ok(apiConstants.includes("recovery/reset-password"));
  assert.ok(apiConstants.includes("/login/public-key"));
  assert.ok(publicKey.includes("/login/public-key"));
});

test("login, session, provider and status routes are public", () => {
  assert.ok(authorization.includes('"/apis/" + ApiGroups.AUTH + "/v1alpha1/login/**"'));
});

test("single-entry build is mounted at /auth", () => {
  assert.ok(spa.includes('"/auth/index.html"'));
  assert.ok(!spa.includes("/console/index.html"));
  assert.ok(!spa.includes("/uc/index.html"));
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
