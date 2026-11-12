/** The auth application is mounted under this base path by the server. */
export const AUTH_BASENAME = "/auth";

export const AUTH_ROUTES = {
  login: "/",
  recovery: "/recovery",
  legal: "/legal",
  signedIn: "/done",
} as const;

export type AuthRoute = (typeof AUTH_ROUTES)[keyof typeof AUTH_ROUTES];
