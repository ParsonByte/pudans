/**
 * Resolves a server-provided post-login destination.
 *
 * Same-origin paths stay on this application; an absolute HTTP(S) URL is
 * allowed because the destination is configured by the instance operator, not
 * by a request parameter. Anything else falls back to the auth landing page.
 */
export function safeRedirectPath(value: string | null | undefined, fallback: string): string {
  if (!value) {
    return fallback;
  }
  try {
    const url = new URL(value, window.location.origin);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      return fallback;
    }
    if (url.origin !== window.location.origin) {
      return url.href;
    }
    return url.pathname + url.search + url.hash;
  } catch {
    return fallback;
  }
}
