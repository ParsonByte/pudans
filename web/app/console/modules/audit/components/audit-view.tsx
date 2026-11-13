import { PageSurface } from "../../../components/page-surface";
import { useAuditPage } from "../composables/use-audit-page";

export function ConsoleAuditView() {
  const { description } = useAuditPage();
  return <PageSurface description={description} />;
}
