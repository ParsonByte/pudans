import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import test from "node:test";

const repo = new URL("../../", import.meta.url).pathname;
const web = repo + "web/";
const entry = readFileSync(web + "packages/api-client/entry/api-client.ts", "utf8");
const navigation = readFileSync(web + "src/hooks/use-auth-navigation.ts", "utf8");
const consoleApp = readFileSync(web + "app/console/app.tsx", "utf8");
const ucApp = readFileSync(web + "app/uc/app.tsx", "utf8");
const setup = readFileSync(
  web + "src/components/setup/modules/setup/composables/use-setup.ts",
  "utf8",
);
const signOut = readFileSync(
  web + "src/components/auth/modules/signed-in/composables/use-sign-out.ts",
  "utf8",
);

test("the single axios instance installs centralized 401 and 403 handlers", () => {
  const apiSrc = readdirSync(web + "packages/api-client/src");
  assert.ok(apiSrc.includes("api"));
  assert.ok(apiSrc.includes("models"));
  assert.ok(!apiSrc.includes("client"));
  assert.ok(entry.includes("normalized.status === 401"));
  assert.ok(entry.includes("normalized.status === 403"));
  assert.ok(entry.includes("redirectToLogin()"));
  assert.ok(entry.includes("redirectToForbidden()"));
  assert.ok(entry.includes("X-Request-Id"));
  assert.ok(entry.includes("Authorization"));
});

test("console disallow check runs before route registration and login redirect", () => {
  const disallowIndex = consoleApp.indexOf("consoleAccessDisallowed");
  const anonymousIndex = consoleApp.indexOf('status === "anonymous"');
  const routerIndex = consoleApp.indexOf("createConsoleRouter(permissions)");
  assert.ok(disallowIndex >= 0);
  assert.ok(anonymousIndex > disallowIndex);
  assert.ok(routerIndex > anonymousIndex);
  assert.ok(consoleApp.includes('return "/uc"'));
});

test("uc never requires console access", () => {
  assert.ok(ucApp.includes("createUcRouter"));
  assert.ok(ucApp.includes("!consoleAccessDisallowed"));
});

test("redirect_uri is whitelisted, encoded and permission-checked", () => {
  for (const prefix of ["/console", "/uc", "/setup", "/auth/legal"]) {
    assert.ok(navigation.includes(prefix), prefix);
  }
  assert.ok(navigation.includes("encodeURIComponent"));
  assert.ok(navigation.includes("isRoutePermitted"));
  assert.ok(navigation.includes("sanitizeRedirectUri"));
  assert.ok(navigation.includes("/auth?redirect_uri="));
});

test("logout lands on the signed-in confirmation route", () => {
  assert.ok(signOut.includes("AUTH_ROUTES.signedIn"));
  const shell = readFileSync(web + "src/layouts/app-shell.tsx", "utf8");
  assert.ok(shell.includes('"/auth/signed-in"'));
});

test("setup submits through the api-client entry with the backend contract", () => {
  assert.ok(setup.includes("setupSystem"));
  assert.ok(setup.includes("externalUrl"));
  assert.ok(!setup.includes("fetch("));
  assert.ok(!setup.includes("axios"));
});
