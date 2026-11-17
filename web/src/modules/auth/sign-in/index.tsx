import { LoginForm } from "./components/login-form";

type SignInViewProps = {
  redirectTo?: string;
};

export function SignInView({ redirectTo }: SignInViewProps) {
  return <LoginForm redirectTo={redirectTo} />;
}
