import { useEffect, useMemo, useRef, type ReactNode } from "react";
import { useAppDispatch, useAppSelector } from "../store/hooks";
import {
  CONSOLE_UI_PERMISSIONS,
  hasAnyPermission,
  loadSession,
  type AuthStatus,
  type CurrentUser,
} from "../store/auth-slice";

export {
  ALL_PERMISSIONS,
  CONSOLE_UI_PERMISSIONS,
  type AuthStatus,
  type CurrentUser,
} from "../store/auth-slice";

export interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  permissions: readonly string[];
  isAnonymous: boolean;
  canAccessConsole: boolean;
  hasPermission: (...tags: string[]) => boolean;
}

/**
 * Global session layer shared by every application shell (auth, console, uc).
 *
 * The actual request and state live in the Redux Toolkit auth slice; this
 * provider only dispatches the one-time load, and every consumer (layouts,
 * guards, feature views) reads the same memoised value through {@link useAuth}.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const dispatch = useAppDispatch();
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) {
      return;
    }
    startedRef.current = true;
    void dispatch(loadSession());
  }, [dispatch]);

  return <>{children}</>;
}

export function useAuth(): AuthContextValue {
  const { status, user, permissions } = useAppSelector((state) => state.auth);

  return useMemo<AuthContextValue>(() => {
    const hasPermission = (...tags: string[]): boolean => hasAnyPermission(permissions, tags);
    return {
      status,
      user,
      permissions,
      isAnonymous: status !== "authenticated",
      canAccessConsole: hasAnyPermission(permissions, [...CONSOLE_UI_PERMISSIONS]),
      hasPermission,
    };
  }, [status, user, permissions]);
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
