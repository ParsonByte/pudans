import { redirect } from "@tanstack/react-router";
import { toast } from "sonner";

import { getSetupStatus } from "./setup-status";

const SETUP_STATUS_ERROR = "无法获取系统初始化状态，请检查服务是否可用。";

type SetupGuardContext = {
  location: { pathname: string };
};

export async function guardSetupFlow({ location }: SetupGuardContext) {
  if (location.pathname !== "/sign-in" && location.pathname !== "/setup") {
    return;
  }

  const status = await getSetupStatus();

  if (!status.available) {
    toast.error(SETUP_STATUS_ERROR);
    return;
  }

  if (status.setupRequired && location.pathname === "/sign-in") {
    throw redirect({ to: "/setup", replace: true });
  }

  if (!status.setupRequired && location.pathname === "/setup") {
    throw redirect({ to: "/sign-in", replace: true });
  }
}
