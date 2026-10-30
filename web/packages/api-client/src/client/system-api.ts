import type { HttpClient } from "./types";

export interface SetupPayload {
  username: string;
  password: string;
  email?: string;
  siteTitle: string;
  siteUrl: string;
  language?: string;
}

export const PUBLIC_SETUP_PATH =
  "/apis/public.api.pudans.tolim.cn/v1alpha1/system/setup";

/**
 * The generated SystemV1alpha1PublicApi publishes the setup operation as JSON,
 * while the backend endpoint is form-urlencoded. Keep this thin, explicit
 * wrapper so the generated API surface is not bypassed for other resources.
 */
export class SystemApi {
  constructor(private readonly http: HttpClient) {}

  setup(payload: SetupPayload): Promise<void> {
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

    return this.http.post<void>(PUBLIC_SETUP_PATH, body, {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });
  }
}
