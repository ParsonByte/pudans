import { ParameterGroupPanel } from "../components/parameter-group-panel";
import { getParameterGroup } from "../lib/parameter-fields";

export function SiteSettingsPage() {
  return <ParameterGroupPanel group={getParameterGroup("site")} />;
}
