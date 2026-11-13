import { PageSurface } from "../../../components/page-surface";
import { useProfilePage } from "../composables/use-profile-page";

export function UcProfileView() {
  const { title, description } = useProfilePage();
  return <PageSurface title={title} description={description} />;
}
