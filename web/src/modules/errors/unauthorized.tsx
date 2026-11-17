import { BackHomeActions, ErrorPage } from "./components/error-page";

export function UnauthorizedError() {
  return (
    <ErrorPage
      status="401"
      title="未授权访问"
      description="请使用具有相应权限的账号登录后再访问此资源。"
      actions={<BackHomeActions />}
    />
  );
}
