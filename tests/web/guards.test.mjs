import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const repo = new URL("../../", import.meta.url).pathname;
const guards = readFileSync(repo + "web/src/router/guards.tsx", "utf8");
const navigation = readFileSync(repo + "web/src/hooks/use-auth-navigation.ts", "utf8");
const clientIndex = readFileSync(repo + "web/packages/api-client/src/client/index.ts", "utf8");
const setup = readFileSync(
  repo + "web/src/components/setup/modules/setup/composables/use-setup.ts",
  "utf8",
);

test("the api-client installs centralized 401 and 403 handlers", () => {
  assert.ok(clientIndex.includes("redirectToLogin"));
  assert.ok(clientIndex.includes("redirectToForbidden"));
  const http = readFileSync(repo + "web/packages/api-client/src/client/http.ts", "utf8");
  assert.ok(http.includes("options.onUnauthorized"));
  assert.ok(http.includes("options.onForbidden"));
});

test("console disallow check runs before the anonymous redirect", () => {
  const consoleIndex = guards.indexOf('access === "console"');
  const anonymousIndex = guards.indexOf('status === "anonymous"');
  assert.ok(consoleIndex >= 0);
  assert.ok(anonymousIndex > consoleIndex);
  assert.ok(guards.includes('navigate("/uc"'));
});

test("redirect_uri is whitelisted and encoded", () => {
  for (const prefix of ["/console", "/uc", "/setup", "/auth/legal"]) {
    assert.ok(navigation.includes(prefix), prefix);
  }
  assert.ok(navigation.includes("encodeURIComponent"));
  assert.ok(guards.includes("redirect_uri="));
});

test("setup submits through the auth domain client", () => {
  assert.ok(setup.includes("authApiClient.setupSystem"));
  assert.ok(!setup.includes("fetch("));
  assert.ok(!setup.includes("axios"));
});
