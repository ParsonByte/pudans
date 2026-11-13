export const PUDANS_API_PREFIX = "/api";
export const PUDANS_STORAGE_PREFIX = "pudans:";
export const DEFAULT_LOCALE = "en-US";
export const SUPPORTED_LOCALES = ["en-US", "zh-CN"] as const;
export const APP_NAMES = {
  setup: "setup",
  auth: "auth",
  console: "console",
  uc: "uc",
} as const;
export const APP_PATHS = {
  setup: "/setup",
  auth: "/auth",
  console: "/console",
  uc: "/uc",
} as const;

export const ALL_PERMISSIONS = "*";

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

export const BREAKPOINTS = {
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  "2xl": 1536,
} as const;

export const STORAGE_KEYS = {
  colorMode: `${PUDANS_STORAGE_PREFIX}color-mode`,
  accessToken: `${PUDANS_STORAGE_PREFIX}access-token`,
  locale: `${PUDANS_STORAGE_PREFIX}locale`,
  sidebarCollapsed: `${PUDANS_STORAGE_PREFIX}sidebar-collapsed`,
} as const;
