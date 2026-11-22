import type { NextRequest } from "next/server";

export const API_ORIGIN = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";
const SESSION_PATH = "/apis/api.pudans.tolim.cn/v1alpha1/login/session";

type SessionView = {
    authenticated?: boolean;
};

export async function requireAuthentication(request: NextRequest): Promise<boolean> {
    try {
        const response = await fetch(`${API_ORIGIN}${SESSION_PATH}`, {
            cache: "no-store",
            headers: {
                accept: "application/json",
                cookie: request.headers.get("cookie") ?? "",
            },
        });
        const session = (await response.json()) as SessionView;

        return response.ok && session.authenticated === true;
    } catch {
        return false;
    }
}
