import { BackHomeActions, ErrorPage } from "./components/error-page";

export function MaintenanceError() {
  return (
    <ErrorPage
      status="503"
      title="系统维护中"
      description="系统正在维护，暂时无法访问，请稍后再试。"
      actions={<BackHomeActions />}
    />
  );
}
