import { PageSurface } from "../../../components/page-surface";
import { useUsersPage } from "../composables/use-users-page";

export function ConsoleUsersView() {
  const { description } = useUsersPage();
  return <PageSurface description={description} />;
}
