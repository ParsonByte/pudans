import { apiClient } from "@pudans-dev/api-client";

type SetupStatus = { available: true; setupRequired: boolean } | { available: false };

export async function getSetupStatus(): Promise<SetupStatus> {
  try {
    const { data } = await apiClient.login.getLoginStatus();
    return { available: true, setupRequired: Boolean(data.setupRequired) };
  } catch {
    return { available: false };
  }
}
