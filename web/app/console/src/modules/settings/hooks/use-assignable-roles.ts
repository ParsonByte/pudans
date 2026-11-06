import { useEffect, useState } from "react";
import { apiClient } from "@pudans/api-client";

const ROLES_ENDPOINT = "/apis/console.api.pudans.tolim.cn/v1alpha1/roles";
const TEMPLATE_LABEL = "pudans.tolim.cn/role-template";
const HIDDEN_LABEL = "pudans.tolim.cn/hidden";
const DISPLAY_NAME = "rbac.authorization.pudans.tolim.cn/display-name";

const INTERNAL_ROLE_NAMES = new Set(["guest", "anonymous", "authenticated"]);

export interface RoleOption {
  value: string;
  label: string;
}

interface RoleListItem {
  metadata?: {
    name?: string;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
  };
}

export function isAssignableRole(name: string): boolean {
  return name.length > 0 && !INTERNAL_ROLE_NAMES.has(name) && !name.startsWith("role-template-");
}

export function useAssignableRoles(): RoleOption[] {
  const [roles, setRoles] = useState<RoleOption[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    apiClient.http
      .get<{ items?: RoleListItem[] }>(ROLES_ENDPOINT, {
        signal: controller.signal,
        pudansRetry: false as const,
      })
      .then((result) => {
        const options: RoleOption[] = [];
        for (const item of result.items ?? []) {
          const name = item.metadata?.name ?? "";
          const labels = item.metadata?.labels ?? {};
          if (!isAssignableRole(name)) {
            continue;
          }
          if (labels[TEMPLATE_LABEL] === "true" || labels[HIDDEN_LABEL] === "true") {
            continue;
          }
          options.push({
            value: name,
            label: item.metadata?.annotations?.[DISPLAY_NAME] || name,
          });
        }
        options.sort((left, right) => left.label.localeCompare(right.label));
        setRoles(options);
      })
      .catch(() => {
        setRoles([]);
      });
    return () => {
      controller.abort();
    };
  }, []);

  return roles;
}
