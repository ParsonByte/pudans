import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";

const componentsSrc = new URL("../packages/components/src/", import.meta.url).pathname;
const directories = readdirSync(join(componentsSrc, "components"), { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();

test("every component folder has an index barrel and is re-exported", () => {
  const barrel = readFileSync(join(componentsSrc, "components/index.ts"), "utf8");
  const missingFolderBarrels = directories.filter(
    (name) => !existsSync(join(componentsSrc, "components", name, "index.ts")),
  );
  assert.deepEqual(missingFolderBarrels, []);
  const notExported = directories.filter((name) => !barrel.includes('"./' + name + '"'));
  assert.deepEqual(notExported, []);
});

test("component export names are unique across folders", () => {
  const names = new Map();
  const duplicates = [];
  for (const folder of directories) {
    const source = readFileSync(join(componentsSrc, "components", folder, "index.ts"), "utf8");
    const files = [
      ...new Set([...source.matchAll(/from "\.\/([^"]+)"/g)].map((match) => match[1])),
    ];
    for (const file of files) {
      const filePath = join(componentsSrc, "components", folder, file + ".tsx");
      if (!existsSync(filePath)) continue;
      const content = readFileSync(filePath, "utf8");
      const exported = [
        ...content.matchAll(/export (?:const|function|class|interface|type|enum) ([A-Za-z0-9_]+)/g),
        ...content.matchAll(/export \{ ([^}]+) \}/g),
      ];
      for (const match of exported) {
        const tokens = match[1].split(",").map((token) =>
          token
            .trim()
            .split(/\s+as\s+/)
            .pop(),
        );
        for (const name of tokens) {
          if (!name) continue;
          if (names.has(name)) {
            duplicates.push(name + " (" + names.get(name) + " & " + folder + ")");
          } else {
            names.set(name, folder);
          }
        }
      }
    }
  }
  assert.deepEqual(duplicates, [], "duplicate named exports");
});

test("the built library exposes every expected public symbol", async () => {
  const lib = await import("@pudans/components");
  const required = [
    "Button",
    "Input",
    "Dialog",
    "DropdownMenu",
    "Table",
    "EmptyState",
    "ErrorState",
    "ErrorBoundary",
    "Progress",
    "ProgressSteps",
    "Code",
    "CodeBlock",
    "Breadcrumbs",
    "DescriptionList",
    "RelativeTime",
    "FileTypeIcon",
    "ConfirmDialog",
    "ConfirmProvider",
    "useConfirm",
    "SelectDialog",
    "SelectProvider",
    "useSelectOption",
    "ErrorDetailDialog",
    "Accordion",
    "ThemeSwitch",
    "Logo",
    "PoweredBy",
    "LanguageSwitcher",
    "UserBadge",
    "UserPopover",
    "OAuthAppCard",
    "FormField",
    "FormSection",
    "SettingItem",
    "SearchInput",
    "SplitPane",
    "ResponsiveTabs",
    "SizeInput",
    "FileDropZone",
    "Captcha",
    "AnimatedSwap",
    "PudansThemeProvider",
    "usePudansTheme",
    "AppShell",
    "PageLayout",
    "PageContainer",
    "useDelayedHover",
    "useOverflow",
    "useMediaSession",
  ];
  const missing = required.filter((name) => !(name in lib));
  assert.deepEqual(missing, []);
});
