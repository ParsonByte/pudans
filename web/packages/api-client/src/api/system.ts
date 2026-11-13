import { http } from "../../entry/api-client";
import type { SetupPayload } from "../models/system";

const PUBLIC_API_BASE = "/apis/public.api.pudans.tolim.cn/v1alpha1";

/** POST /system/setup (form-urlencoded first-run initialization). */
export function setupSystem(payload: SetupPayload): Promise<void> {
  const body = new URLSearchParams({
    username: payload.username,
    password: payload.password,
    siteTitle: payload.siteTitle,
    externalUrl: payload.siteUrl,
  });
  if (payload.email) {
    body.set("email", payload.email);
  }
  if (payload.language) {
    body.set("language", payload.language);
  }

  return http
    .post<void>(`${PUBLIC_API_BASE}/system/setup`, body, {
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
    })
    .then(() => undefined);
}
