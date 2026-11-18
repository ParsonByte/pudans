import { LoginForm } from "./components/login-form";

type SignInViewProps = {
  redirectTo?: string;
  errorCode?: string;
  errorDetail?: string;
};

export function SignInView({ redirectTo, errorCode, errorDetail }: SignInViewProps) {
  const errorMessage = errorDetail ?? (errorCode ? "登录失败，请稍后重试。" : undefined);

  return <LoginForm redirectTo={redirectTo} errorMessage={errorMessage} />;
}
