import type { ShellSwitchTarget } from "../../src/layouts/app-shell";

/** UC route base is fixed; it is never derived from BASE_URL. */
export const UC_BASE_PATH = "/uc" as const;

export const UC_ROOT_NAME = "uc-root";

export const UC_GROUP_LABELS = {
  account: "groupAccount",
  security: "groupSecurity",
} as const;

export const UC_SWITCH_TARGET: ShellSwitchTarget = {
  href: "/console",
  labelKey: "backToConsole",
  icon: "mdi:view-dashboard-outline",
};
