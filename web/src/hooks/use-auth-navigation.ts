import { useSearchParams } from "react-router-dom";
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
    const path = url.pathname;
    if (!ALLOWED_REDIRECT_PREFIXES.some((prefix) => path === prefix || path.startsWith(prefix + "/"))) {
      return null;
    }
    return path + url.search + url.hash;
  } catch {
    return null;
  }
}

export function currentLocationWithSearch(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function loginLocation(): string {
  const current = currentLocationWithSearch();
  if (current.startsWith("/auth/")) {
    return "/auth/";
  }
  return `/auth/?redirect_uri=${encodeURIComponent(current)}`;
}

export function useRedirectUri(): string | null {
  const [searchParams] = useSearchParams();
  return sanitizeRedirectUri(searchParams.get("redirect_uri"));
}

export function useLandingPath(): string {
  const redirectUri = useRedirectUri();
  const { consoleAccessDisallowed } = useSession();
  // Disallowed-console accounts always land in the user centre, even when a
  // console redirect_uri was supplied.
  if (consoleAccessDisallowed) {
    return "/uc";
  }
  return redirectUri ?? "/console";
}
