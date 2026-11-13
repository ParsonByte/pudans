import { PageSurface } from "../../../components/page-surface";
import { useSecurityPage } from "../composables/use-security-page";

export function UcSecurityView() {
  const { description } = useSecurityPage();
  return <PageSurface description={description} />;
}
