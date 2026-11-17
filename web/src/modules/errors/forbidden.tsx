import { BackHomeActions, ErrorPage } from "./components/error-page";

export function ForbiddenError() {
  return (
    <ErrorPage
      status="403"
      title="禁止访问"
      description="你没有查看此资源所需的权限。"
      actions={<BackHomeActions />}
    />
  );
}
