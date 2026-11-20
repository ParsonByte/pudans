import { toast } from "sonner";

import { getSetupStatus } from "./setup-status";

const SETUP_STATUS_ERROR = "无法获取系统初始化状态，请检查服务是否可用。";

export async function resolveSetupRedirect(pathname: string): Promise<string | null> {
  if (pathname !== "/sign-in" && pathname !== "/setup") {
    return null;
  }

  const status = await getSetupStatus();

  if (!status.available) {
    toast.error(SETUP_STATUS_ERROR);
    return null;
  }

  if (status.setupRequired && pathname === "/sign-in") {
    return "/setup";
  }

  if (!status.setupRequired && pathname === "/setup") {
    return "/sign-in";
  }

  return null;
}
