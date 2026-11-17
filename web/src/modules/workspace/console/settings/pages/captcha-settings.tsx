import { ParameterGroupPanel } from "../components/parameter-group-panel";
import { getParameterGroup } from "../lib/parameter-fields";

export function CaptchaSettingsPage() {
  return <ParameterGroupPanel group={getParameterGroup("captcha")} />;
}
