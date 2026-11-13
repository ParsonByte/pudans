import { PageSurface } from "../../../components/page-surface";
import { useProfilePage } from "../composables/use-profile-page";

export function UcProfileView() {
  const { description } = useProfilePage();
  return <PageSurface description={description} />;
}
