import { BackHomeActions, ErrorPage } from "./components/error-page";

export function TooManyRequestsError() {
  return (
    <ErrorPage
      status="429"
      title="请求过于频繁"
      description="请求触发了风险控制，请稍后再试。"
      actions={<BackHomeActions />}
    />
  );
}
