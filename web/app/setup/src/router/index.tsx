import type { RouteObject } from "react-router-dom";
import { AppLayout } from "../layouts/app-layout";
import { SetupView } from "../modules/setup/setup-view";

export const routes: RouteObject[] = [
  {
    path: "/",
    element: <AppLayout />,
    children: [{ index: true, element: <SetupView /> }],
  },
];
