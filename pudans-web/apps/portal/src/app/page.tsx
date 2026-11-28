import { PortalAppHeader } from "@/components/portal/portal-app-header";
import { PortalExperience } from "@/components/portal/portal-experience";

const authHref = process.env.NEXT_PUBLIC_AUTH_URL ?? "http://localhost:3001";
const consoleHref = process.env.NEXT_PUBLIC_CONSOLE_URL ?? "http://localhost:3003";

export default function PortalPage() {
  return (
    <>
      <PortalAppHeader authHref={authHref} consoleHref={consoleHref} />
      <PortalExperience />
    </>
  );
}