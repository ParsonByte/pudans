import { type SessionView, apiClient } from "@pudans-dev/api-client";

export async function getLoginSession(): Promise<SessionView> {
  return (await apiClient.login.getLoginSession()).data;
}
