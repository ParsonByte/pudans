import { PageSurface } from "../../../components/page-surface";
import { useUsersPage } from "../composables/use-users-page";

export function ConsoleUsersView() {
  const { title, description } = useUsersPage();
  return <PageSurface title={title} description={description} />;
}
