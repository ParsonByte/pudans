import { ConsolePage } from "@/modules/workspace/console/components/console-page";

import { DevicesSection } from "./components/devices-section";
import { PasskeysSection } from "./components/passkeys-section";
import { PasswordSection } from "./components/password-section";
import { TwoFactorSection } from "./components/two-factor-section";

export function AccountSecurityView() {
  return (
    <ConsolePage title="安全中心">
      <PasswordSection />
      <TwoFactorSection />
      <PasskeysSection />
      <DevicesSection />
    </ConsolePage>
  );
}
