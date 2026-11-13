import type { RegisteredMenu } from "@pudans/shared";
import type { ShellSwitchTarget } from "../../../src/layouts/app-shell";

/** Console route base is fixed; it is never derived from BASE_URL. */
export const CONSOLE_BASE_PATH = "/console" as const;

/** Name of the layout route modules attach to with parentName. */
export const CONSOLE_ROOT_NAME = "console-root";

export const CONSOLE_SWITCH_TARGET: ShellSwitchTarget = {
  href: "/uc",
  labelKey: "backToUc",
  icon: "mdi:account-circle-outline",
};

export type ConsoleMenu = readonly RegisteredMenu[];
