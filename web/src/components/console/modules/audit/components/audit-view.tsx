import { PagePlaceholder } from "../../../components/page-placeholder";
import { useAuditPage } from "../composables/useAuditPage";

export function ConsoleAuditView() {
  const { title, description } = useAuditPage();
  return <PagePlaceholder title={title} description={description} />;
}
