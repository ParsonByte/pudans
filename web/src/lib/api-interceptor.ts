import { axiosInstance } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { getCookie } from "@/lib/cookies";

const IGNORED_PATHS = ["/sign-in", "/forgot-password", "/setup", "/429"];

export type NavigationAuditContext = {
  auditId: string;
  traceFlowId: string;
};

let navigationAuditContext: NavigationAuditContext | null = null;

export function setNavigationAuditContext(context: NavigationAuditContext | null) {
  navigationAuditContext = context;
}

export function installApiInterceptors() {
  axiosInstance.interceptors.request.use((config) => {
    if (navigationAuditContext) {
      config.headers.set("X-Parent-Audit-Id", navigationAuditContext.auditId);
      config.headers.set("X-Trace-Flow-Id", navigationAuditContext.traceFlowId);
    }
    const csrfToken = getCookie("XSRF-TOKEN");
    if (csrfToken) {
      config.headers.set("X-XSRF-TOKEN", csrfToken);
    }
    return config;
  });
  axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (
        isAxiosError<{ type?: string; methods?: string[] }>(error) &&
        error.response?.status === 403 &&
        typeof error.response.data?.type === "string" &&
        error.response.data.type.endsWith("sudo-required")
      ) {
        window.dispatchEvent(
          new CustomEvent("pudans:sudo-required", {
            detail: { methods: error.response.data.methods ?? [] },
          }),
        );
      }
      if (
        isAxiosError(error) &&
        error.response?.status === 429 &&
        !IGNORED_PATHS.some((path) => window.location.pathname.startsWith(path))
      ) {
        window.location.assign("/429");
      }
      return Promise.reject(error);
    },
  );
}
