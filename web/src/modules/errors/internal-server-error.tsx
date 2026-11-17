import { BackHomeActions, ErrorPage } from "./components/error-page";

export function InternalServerError() {
  return (
    <ErrorPage
      status="500"
      title="服务器出错了"
      description="服务器暂时无法完成请求，请稍后重试。"
      actions={<BackHomeActions />}
    />
  );
}
