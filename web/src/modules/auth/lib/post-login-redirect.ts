export function resolvePostLoginRedirect(redirectTo?: string, fallback?: string): string {
  if (typeof window === "undefined") {
    return "/";
  }

  try {
    const url = new URL(redirectTo || fallback || "/", window.location.origin);

    if (url.origin !== window.location.origin) {
      return "/";
    }

    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return "/";
  }
}
