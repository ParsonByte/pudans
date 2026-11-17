import { MailTestDialog } from "../components/mail-test-dialog";
import { ParameterGroupPanel } from "../components/parameter-group-panel";
import { getParameterGroup } from "../lib/parameter-fields";

export function MailSettingsPage() {
  return (
    <ParameterGroupPanel
      group={getParameterGroup("mail")}
      sectionActions={{ 发信: <MailTestDialog /> }}
    />
  );
}
