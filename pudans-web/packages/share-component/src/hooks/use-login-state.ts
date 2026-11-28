"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { apiClient, type UserInfo } from "@pudans-dev/api-client";

export type LoginStatus = "checking" | "authenticated" | "anonymous" | "error";

export type LoginState = {
  error: unknown;
  isAnonymous: boolean;
  isAuthenticated: boolean;
  isLoading: boolean;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  status: LoginStatus;
  user: UserInfo | null;
};

export type LoginStateOptions = {
  enabled?: boolean;
  refreshOnFocus?: boolean;
};

export function useLoginState({
  enabled = true,
  refreshOnFocus = true,
}: LoginStateOptions = {}): LoginState {
  const [status, setStatus] = useState<LoginStatus>("checking");
  const [user, setUser] = useState<UserInfo | null>(null);
  const [error, setError] = useState<unknown>(null);
  const requestIdRef = useRef(0);

  const refresh = useCallback(async () => {
    const requestId = ++requestIdRef.current;

    try {
      const response = await apiClient.iam.session.me1();

      if (requestId !== requestIdRef.current) {
        return;
      }

      setUser(response.data ?? null);
      setStatus(response.data ? "authenticated" : "anonymous");
      setError(null);
    } catch (caught) {
      if (requestId !== requestIdRef.current) {
        return;
      }

      setUser(null);
      if ((caught as { response?: { status?: number } }).response?.status === 401) {
        setStatus("anonymous");
        setError(null);
      } else {
        setStatus("error");
        setError(caught);
      }
    }
  }, []);

  const logout = useCallback(async () => {
    await apiClient.iam.authentication.logout({});
    setUser(null);
    setStatus("anonymous");
    setError(null);
  }, []);

  useEffect(() => {
    if (enabled) {
      void refresh();
    }
  }, [enabled, refresh]);

  useEffect(() => {
    if (!enabled || !refreshOnFocus) {
      return;
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void refresh();
      }
    };
    const handleFocus = () => {
      void refresh();
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    window.addEventListener("focus", handleFocus);

    return () => {
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.removeEventListener("focus", handleFocus);
    };
  }, [enabled, refresh, refreshOnFocus]);

  return useMemo(
    () => ({
      error,
      isAnonymous: status === "anonymous",
      isAuthenticated: status === "authenticated",
      isLoading: status === "checking",
      logout,
      refresh,
      status,
      user,
    }),
    [error, logout, refresh, status, user]
  );
}