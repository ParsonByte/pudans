import { API_ORIGIN } from "@/routes/_authenticated/route";

const SETUP_STATUS_PATH = "/apis/api.pudans.tolim.cn/v1alpha1/login/status";

export async function readSetupRequired(): Promise<boolean | null> {
    try {
        const response = await fetch(`${API_ORIGIN}${SETUP_STATUS_PATH}`, {
            cache: "no-store",
            headers: { accept: "application/json" },
        });

        if (!response.ok) {
            return null;
        }

        const status = (await response.json()) as { setupRequired?: boolean };

        return status.setupRequired === true;
    } catch {
        return null;
    }
}
