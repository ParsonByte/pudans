import { Outlet } from "react-router-dom";
import { AppFrame } from "../components/app-frame";
import { useAppName } from "../composables/use-app-name";

export function AppLayout() {
  const appName = useAppName();
  return (
    <AppFrame appName={appName}>
      <Outlet />
    </AppFrame>
  );
}
