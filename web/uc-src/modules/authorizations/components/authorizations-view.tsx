import { PageSurface } from "../../../components/page-surface";
import { useAuthorizationsPage } from "../composables/use-authorizations-page";

export function UcAuthorizationsView() {
  const { title, description } = useAuthorizationsPage();
  return <PageSurface title={title} description={description} />;
}
