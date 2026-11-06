import type { ReactNode } from "react";

export interface AppFrameProps {
  appName: string;
  children: ReactNode;
}

export function AppFrame({ appName, children }: AppFrameProps) {
  return (
    <div data-app={appName} className="min-h-screen">
      {children}
    </div>
  );
}
