import { PagePlaceholder } from "../../../components/page-placeholder";
import { useUsersPage } from "../composables/useUsersPage";

export function ConsoleUsersView() {
  const { title, description } = useUsersPage();
  return <PagePlaceholder title={title} description={description} />;
}
