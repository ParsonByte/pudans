import { AppHeader } from "@pudans-dev/share-component";

type PortalAppHeaderProps = {
  authHref: string;
  consoleHref: string;
};

export function PortalAppHeader({ authHref, consoleHref }: PortalAppHeaderProps) {
  return (
    <div className="portal-app-header">
      <AppHeader consoleHref={consoleHref} loginHref={authHref} />
    </div>
  );
}