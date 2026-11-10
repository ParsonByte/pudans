import type { ReactNode } from "react";
import { Box } from "@mui/material";

export interface AppFrameProps {
  appName: string;
  children: ReactNode;
}

export function AppFrame({ appName, children }: AppFrameProps) {
  return (
    <Box data-app={appName} sx={{ minHeight: "100vh" }}>
      {children}
    </Box>
  );
}
