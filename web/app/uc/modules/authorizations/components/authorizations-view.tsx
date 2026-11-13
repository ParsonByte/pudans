import { PageSurface } from "../../../components/page-surface";
import { useAuthorizationsPage } from "../composables/use-authorizations-page";

export function UcAuthorizationsView() {
  const { description } = useAuthorizationsPage();
  return <PageSurface description={description} />;
}
