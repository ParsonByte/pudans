import { BackHomeActions, ErrorPage } from "./components/error-page";

export function NotFoundError() {
  return (
    <ErrorPage
      status="404"
      title="页面不存在"
      description="你访问的页面可能已被移除，或地址有误。"
      actions={<BackHomeActions />}
    />
  );
}
