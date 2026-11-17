import { AuditSettingsForm } from "../components/audit-settings-form";
import { ParameterGroupPanel } from "../components/parameter-group-panel";
import { getParameterGroup } from "../lib/parameter-fields";

export function AuditSettingsPage() {
  return (
    <ParameterGroupPanel
      group={getParameterGroup("audit")}
      render={(data) => <AuditSettingsForm data={data} />}
    />
  );
}
