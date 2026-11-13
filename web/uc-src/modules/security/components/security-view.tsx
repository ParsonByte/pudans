import { PageSurface } from "../../../components/page-surface";
import { useSecurityPage } from "../composables/use-security-page";

export function UcSecurityView() {
  const { title, description } = useSecurityPage();
  return <PageSurface title={title} description={description} />;
}
