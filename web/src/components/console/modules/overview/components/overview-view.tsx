import { PagePlaceholder } from "../../../components/page-placeholder";
import { useOverviewPage } from "../composables/useOverviewPage";

export function ConsoleOverviewView() {
  const { title, description } = useOverviewPage();
  return <PagePlaceholder title={title} description={description} />;
}
