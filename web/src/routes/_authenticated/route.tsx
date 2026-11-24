import type { NextRequest } from "next/server";

import type { Navigation } from "@pudans-dev/api-client";

import { HOME_WORKSPACE_KEY, workspaceBasePath, workspaceKey } from "@/composables/workspace-navigation";

export const API_ORIGIN = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";
const SESSION_PATH = "/apis/api.pudans.tolim.cn/v1alpha1/login/session";
const NAVIGATION_PATH = "/apis/api.pudans.tolim.cn/v1alpha1/navigation";

type SessionView = {
    authenticated?: boolean;
};

async function fetchApi<T>(request: NextRequest, path: string): Promise<T | undefined> {
    try {
        const response = await fetch(`${API_ORIGIN}${path}`, {
            cache: "no-store",
            headers: {
                accept: "application/json",
                cookie: request.headers.get("cookie") ?? "",
            },
        });

        return response.ok ? ((await response.json()) as T) : undefined;
    } catch {
        return undefined;
    }
}

export async function requireAuthentication(request: NextRequest): Promise<boolean> {
    const session = await fetchApi<SessionView>(request, SESSION_PATH);

    return session?.authenticated === true;
}

export async function resolveDefaultWorkspacePath(request: NextRequest): Promise<string | undefined> {
    const navigation = await fetchApi<Navigation>(request, NAVIGATION_PATH);
    const workspaces = navigation?.workspaces ?? [];
    const workspace = workspaces.find((candidate) => workspaceKey(candidate) === HOME_WORKSPACE_KEY) ?? workspaces[0];

    return workspace ? workspaceBasePath(workspace) : undefined;
}
