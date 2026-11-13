import { PageSurface } from "../../../components/page-surface";
import { useAuditPage } from "../composables/use-audit-page";

export function ConsoleAuditView() {
  const { title, description } = useAuditPage();
  return <PageSurface title={title} description={description} />;
}
