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
  assert.ok(source.includes("meta?.parentName"));
});

test("console and uc routers use fixed basenames", () => {
  const consoleRouter = readFileSync(join(web, "app/console/router/index.tsx"), "utf8");
  const ucRouter = readFileSync(join(web, "app/uc/router/index.tsx"), "utf8");
  assert.ok(consoleRouter.includes("basename: CONSOLE_BASE_PATH"));
  assert.ok(ucRouter.includes("basename: UC_BASE_PATH"));
  const consoleConfig = readFileSync(join(web, "app/console/router/routes.config.tsx"), "utf8");
  const ucConfig = readFileSync(join(web, "app/uc/router/routes.config.tsx"), "utf8");
  assert.ok(consoleConfig.includes('"/console"'));
  assert.ok(ucConfig.includes('"/uc"'));
});

test("console menus are permission gated and generated from route meta", () => {
  const dashboard = readFileSync(join(web, "app/console/modules/dashboard/module.ts"), "utf8");
  const users = readFileSync(join(web, "app/console/modules/users/module.ts"), "utf8");
  const audit = readFileSync(join(web, "app/console/modules/audit/module.ts"), "utf8");
  assert.ok(dashboard.includes("DASHBOARD_VIEW"));
  assert.ok(users.includes("USERS_MANAGE"));
  assert.ok(audit.includes("AUDIT_VIEW"));
  assert.ok(dashboard.includes('group: "overview"'));
  assert.ok(users.includes('group: "access"'));
});

test("layouts contain no hardcoded navigation items", () => {
  const shell = readFileSync(join(web, "src/layouts/app-shell.tsx"), "utf8");
  assert.ok(shell.includes("groupMenu(menu)"));
  assert.ok(shell.includes("menu"));
  assert.ok(!shell.includes('to="/console/users"'));
  assert.ok(!shell.includes('to="/uc/profile"'));
  assert.ok(!shell.includes("NAV_ITEMS"));
  for (const app of ["console", "uc"]) {
    for (const file of walk(join(web, "app", app))) {
      if (!/\.(ts|tsx)$/.test(file)) continue;
      const source = readFileSync(file, "utf8");
      assert.ok(!source.includes("NAV_ITEMS"), "hardcoded navigation in " + file);
    }
  }
});

test("vite builds auth, console and uc app entries into ui-assets", () => {
  const vite = readFileSync(join(web, "vite.config.ts"), "utf8");
  assert.ok(vite.includes("auth: fileURLToPath"));
  assert.ok(vite.includes("console: fileURLToPath"));
  assert.ok(vite.includes("uc: fileURLToPath"));
  assert.ok(vite.includes("DEV_SERVER_PORT = 3000"));
  assert.ok(vite.includes("ui-assets"));
  for (const entry of ["auth", "console", "uc"]) {
    const html = readFileSync(join(web, entry + ".html"), "utf8");
    assert.ok(html.includes('/app/' + entry + '/main.tsx'), entry);
  }
});

test("dashboard shell has page and icon transitions", () => {
  const shell = readFileSync(join(web, "src/layouts/app-shell.tsx"), "utf8");
  assert.ok(shell.includes("SwitchTransition"));
  assert.ok(shell.includes("CSSTransition"));
  assert.ok(shell.includes("styles.pageEnter"));
  assert.ok(shell.includes("item.iconActive"));
  const dashboard = readFileSync(join(web, "app/console/modules/dashboard/module.ts"), "utf8");
  assert.ok(dashboard.includes("iconActive"));
  const css = readFileSync(join(web, "src/layouts/app-shell.module.css"), "utf8");
  assert.ok(css.includes(".pageEnterActive"));
  assert.ok(css.includes("var(--pd-duration-fast)"));
});

test("signed-in and recovery routes are reachable from auth links", () => {
  const authRouter = readFileSync(join(web, "app/auth/router/index.tsx"), "utf8");
  assert.ok(authRouter.includes('path: "recovery"'));
  assert.ok(authRouter.includes('path: "legal"'));
  assert.ok(authRouter.includes('path: "signed-in"'));
  const footer = readFileSync(join(web, "src/components/auth/components/auth-footer.tsx"), "utf8");
  assert.ok(footer.includes('/auth/legal?tab='));
});
