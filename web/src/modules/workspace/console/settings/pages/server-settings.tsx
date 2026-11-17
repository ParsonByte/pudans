import { ParameterGroupPanel } from "../components/parameter-group-panel";
import { ServerSettingsForm } from "../components/server-settings-form";
import { getParameterGroup } from "../lib/parameter-fields";

export function ServerSettingsPage() {
  return (
    <ParameterGroupPanel
      group={getParameterGroup("server")}
      render={(data) => <ServerSettingsForm data={data} />}
    />
  );
}
