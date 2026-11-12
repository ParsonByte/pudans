/**
 * Single source of visible product branding.
 *
 * The default installation brand is Pudans. Deployments may override the
 * values through Vite environment variables without touching component code.
 */
const env = import.meta.env;

export const BRAND_NAME = (env.VITE_BRAND_NAME as string | undefined)?.trim() || "Pudans";
export const BRAND_WORDMARK = (
  (env.VITE_BRAND_WORDMARK as string | undefined)?.trim() || BRAND_NAME
).toUpperCase();
export const BRAND_MARK = (env.VITE_BRAND_MARK as string | undefined)?.trim() || "P";
export const BRAND_SUPPORT_EMAIL =
  (env.VITE_BRAND_SUPPORT_EMAIL as string | undefined)?.trim() || "support@pudans.tolim.cn";
export const BRAND_HOME_URL = (env.VITE_BRAND_HOME_URL as string | undefined)?.trim() || "/auth/";
export const APP_NAME = BRAND_NAME;
