import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { apiClient, type UserView } from "@pudans/api-client";

const CURRENT_USER_PATH = "/apis/uc.api.pudans.tolim.cn/v1alpha1/users/-";
const UI_PERMISSIONS_PATH =
  "/apis/console.api.pudans.tolim.cn/v1alpha1/users/-/ui-permissions";

const ALL_PERMISSIONS = "*";

/** Console ui permission tags; holding any of them proves console access. */
export const CONSOLE_UI_PERMISSIONS = new Set([
  "dashboard:view",
  "users:manage",
  "roles:manage",
  "audit:view",
  "risk:manage",
  "webhooks:manage",
  "settings:manage",
  "auth-providers:manage",
  "plugins:manage",
  "ops:view",
]);

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface CurrentUser {
  name: string;
  displayName: string;
  email?: string;
  avatar?: string;
}

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  permissions: readonly string[];
  isAnonymous: boolean;
  canAccessConsole: boolean;
  hasPermission: (...tags: string[]) => boolean;
}

const LOADING_STATE: AuthContextValue = {
  status: "loading",
  user: null,
  permissions: [],
  isAnonymous: true,
  canAccessConsole: false,
  hasPermission: () => false,
};

const ANONYMOUS_STATE: AuthContextValue = {
  status: "anonymous",
  user: null,
  permissions: [],
  isAnonymous: true,
  canAccessConsole: false,
  hasPermission: () => false,
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function toCurrentUser(view: UserView): CurrentUser {
  const name = view.metadata?.name ?? "";
  return {
    name,
    displayName: view.spec?.displayName || name,
    email: view.spec?.email || undefined,
    avatar: view.spec?.avatar || undefined,
  };
}

function toPermissions(payload: unknown): readonly string[] {
  return Array.isArray(payload) ? payload : [];
}

function hasAny(
  permissions: readonly string[],
  tags: string[],
): boolean {
  if (permissions.includes(ALL_PERMISSIONS)) {
    return true;
  }
  return tags.some((tag) => permissions.includes(tag));
}

/**
 * Global session layer shared by every application shell (auth, console, uc).
 *
 * Mirrors the Halo single-page setup: the current user and the aggregated ui
 * permissions are fetched exactly once per application load, published through
 * this provider, and every consumer (layouts, guards, feature views) reads the
 * same state via {@link useAuth} / {@link usePermissions}. An anonymous or
 * expired session degrades to the anonymous state instead of throwing.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const startedRef = useRef(false);
  const [state, setState] = useState<AuthContextValue>(LOADING_STATE);

  useEffect(() => {
    if (startedRef.current) {
      return;
    }
    startedRef.current = true;
    let cancelled = false;

    const request = { pudansRetry: false as const };
    Promise.allSettled([
      apiClient.http.get<UserView>(CURRENT_USER_PATH, request),
      apiClient.http.get<readonly string[]>(UI_PERMISSIONS_PATH, request),
    ]).then(([userResult, permissionsResult]) => {
      if (cancelled) {
        return;
      }
      if (userResult.status !== "fulfilled") {
        setState(ANONYMOUS_STATE);
        return;
      }
      const user = toCurrentUser(userResult.value);
      const permissions = toPermissions(
        permissionsResult.status === "fulfilled"
          ? permissionsResult.value
          : [],
      );
      setState({
        status: "authenticated",
        user,
        permissions,
        isAnonymous: false,
        canAccessConsole: hasAny(permissions, [...CONSOLE_UI_PERMISSIONS]),
        hasPermission: (...tags: string[]) => hasAny(permissions, tags),
      });
    });

    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo(() => state, [state]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    return LOADING_STATE;
  }
  return context;
}

export interface PermissionsHookValue {
  permissions: readonly string[];
  canAccessConsole: boolean;
  hasPermission: (...tags: string[]) => boolean;
}

export function usePermissions(): PermissionsHookValue {
  const { permissions, canAccessConsole, hasPermission } = useAuth();
  return useMemo(
    () => ({ permissions, canAccessConsole, hasPermission }),
    [permissions, canAccessConsole, hasPermission],
  );
}
