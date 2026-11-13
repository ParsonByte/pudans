import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const web = new URL("../../web/", import.meta.url).pathname;

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

test("route registration filters permissions before building routes and menus", () => {
  const source = readFileSync(join(web, "src/router/plugin-routes.tsx"), "utf8");
  const filterIndex = source.indexOf("filterRoutes");
  const menuIndex = source.indexOf("collectMenu(nodes");
  assert.ok(filterIndex >= 0);
  assert.ok(menuIndex > filterIndex);
  assert.ok(source.includes("deniedPaths.push"));
  assert.ok(source.includes("meta?.menu"));
  assert.ok(source.includes("meta?.permissions"));
  assert.ok(source.includes("parentName"));
});

test("console and uc routers use fixed basenames", () => {
  const consoleRouter = readFileSync(join(web, "console-src/router/index.tsx"), "utf8");
  const ucRouter = readFileSync(join(web, "uc-src/router/index.tsx"), "utf8");
  assert.ok(consoleRouter.includes('basename: CONSOLE_BASE_PATH'));
  assert.ok(ucRouter.includes('basename: UC_BASE_PATH'));
  const consoleConfig = readFileSync(join(web, "console-src/router/routes.config.tsx"), "utf8");
  const ucConfig = readFileSync(join(web, "uc-src/router/routes.config.tsx"), "utf8");
  assert.ok(consoleConfig.includes('"/console"'));
  assert.ok(ucConfig.includes('"/uc"'));
});

test("console menus are permission gated and generated from route meta", () => {
  const dashboard = readFileSync(join(web, "console-src/modules/dashboard/module.ts"), "utf8");
  const users = readFileSync(join(web, "console-src/modules/users/module.ts"), "utf8");
  const audit = readFileSync(join(web, "console-src/modules/audit/module.ts"), "utf8");
  assert.ok(dashboard.includes("DASHBOARD_VIEW"));
  assert.ok(users.includes("USERS_MANAGE"));
  assert.ok(audit.includes("AUDIT_VIEW"));
  assert.ok(dashboard.includes("group: \"overview\""));
  assert.ok(users.includes("group: \"access\""));
});

test("layouts contain no hardcoded navigation items", () => {
  const shell = readFileSync(join(web, "src/layouts/app-shell.tsx"), "utf8");
  assert.ok(shell.includes("menu.map"));
  assert.ok(shell.includes("menu.filter"));
  assert.ok(!shell.includes('to="/console/users"'));
  assert.ok(!shell.includes('to="/uc/profile"'));
  assert.ok(!shell.includes("NAV_ITEMS"));
  for (const app of ["console-src", "uc-src"]) {
    for (const file of walk(join(web, app))) {
      if (!/\.(ts|tsx)$/.test(file)) continue;
      const source = readFileSync(file, "utf8");
      assert.ok(!source.includes("NAV_ITEMS"), "hardcoded navigation in " + file);
    }
  }
});

test("vite builds auth, console and uc entries into ui-assets", () => {
  const vite = readFileSync(join(web, "vite.config.ts"), "utf8");
  assert.ok(vite.includes("auth: fileURLToPath"));
  assert.ok(vite.includes("console: fileURLToPath"));
  assert.ok(vite.includes("uc: fileURLToPath"));
  assert.ok(vite.includes("DEV_SERVER_PORT = 3000"));
  assert.ok(vite.includes("ui-assets"));
});
