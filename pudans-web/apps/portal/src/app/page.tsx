import { PortalAppHeader } from "@/components/portal/portal-app-header";
import { PortalExperience } from "@/components/portal/portal-experience";

const authHref = process.env.NEXT_PUBLIC_AUTH_URL;
const consoleHref = process.env.NEXT_PUBLIC_IAM_CONSOLE_URL;

export default function PortalPage() {
  return (
    <>
      <PortalAppHeader authHref={authHref} consoleHref={consoleHref} />
      <PortalExperience />
    </>
  );
}