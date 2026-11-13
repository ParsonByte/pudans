import { PageSurface } from "../../../components/page-surface";
import { useDashboardPage } from "../composables/use-dashboard-page";

export function ConsoleDashboardView() {
  const { title, description } = useDashboardPage();
  return <PageSurface title={title} description={description} />;
}
