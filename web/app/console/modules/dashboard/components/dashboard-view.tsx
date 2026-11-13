import { PageSurface } from "../../../components/page-surface";
import { useDashboardPage } from "../composables/use-dashboard-page";

export function ConsoleDashboardView() {
  const { description } = useDashboardPage();
  return <PageSurface description={description} />;
}
