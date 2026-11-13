import { useSearchParams } from "react-router-dom";
import { isRoutePermitted } from "@pudans/shared";
import { useSession } from "../session/use-session";

/** Pages a redirect_uri may point at after authentication completes. */
export const ALLOWED_REDIRECT_PREFIXES = ["/console", "/uc", "/setup", "/auth/legal"] as const;

export function sanitizeRedirectUri(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  try {
    const url = new URL(value, window.location.origin);
    if (url.origin !== window.location.origin) {
      return null;
    }
    if (value.startsWith("//") || value.includes("\\")) {
      return null;
    }
    const path = url.pathname;
    if (!ALLOWED_REDIRECT_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix + "/"))) {
      return null;
    }
    return path + url.search + url.hash;
  } catch {
    return null;
  }
}

/** Full-page navigation between the separately bundled console/uc/auth apps. */
export function navigateToApp(href: string): void {
  window.location.assign(href);
}

/** Same as navigateToApp, but replaces the current history entry. */
export function replaceWithApp(href: string): void {
  window.location.replace(href);
}

export function currentLocationWithSearch(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function loginLocation(): string {
  const current = currentLocationWithSearch();
  if (current.startsWith("/auth")) {
    return "/auth";
  }
  return `/auth?redirect_uri=${encodeURIComponent(current)}`;
}

export function useRedirectUri(): string | null {
  const [searchParams] = useSearchParams();
  return sanitizeRedirectUri(searchParams.get("redirect_uri"));
}

/**
 * Resolves the post-login destination.
 *
 * Order matters:
 * 1. a disallowed-console account always lands in UC, even with a console
 *    redirect_uri (the console restriction outranks redirect_uri);
 * 2. a whitelisted redirect_uri is used only when its route policy passes;
 * 3. otherwise the default is /console, falling back to /uc.
 */
export function useLandingPath(): string {
  const redirectUri = useRedirectUri();
  const { consoleAccessDisallowed, hasPermission } = useSession();

  if (consoleAccessDisallowed) {
    if (redirectUri?.startsWith("/uc") && isRoutePermitted(redirectUri, hasPermission)) {
      return redirectUri;
    }
    return "/uc";
  }

  if (redirectUri) {
    if (isRoutePermitted(redirectUri, hasPermission)) {
      return redirectUri;
    }
    return "/forbidden";
  }

  return "/console";
}
