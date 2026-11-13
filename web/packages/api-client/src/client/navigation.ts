/**
 * Central navigation side-effects for the Axios response interceptor.
 *
 * Pages never decide how to react to an expired session or a missing
 * permission; the api-client does it once for every domain client.
 */
const AUTH_PATH_PREFIX = "/auth";

function currentLocation(): string {
  return `${window.location.pathname}${window.location.search}`;
}

export function redirectToLogin(): void {
  if (typeof window === "undefined") {
    return;
  }
  if (window.location.pathname.startsWith(AUTH_PATH_PREFIX)) {
    return;
  }
  const redirectUri = encodeURIComponent(currentLocation());
  window.location.assign(`${AUTH_PATH_PREFIX}/?redirect_uri=${redirectUri}`);
}

export function redirectToForbidden(): void {
  if (typeof window === "undefined") {
    return;
  }
  if (window.location.pathname === "/forbidden") {
    return;
  }
  window.location.assign("/forbidden");
}
