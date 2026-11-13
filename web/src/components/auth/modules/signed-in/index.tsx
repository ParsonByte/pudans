import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { AUTH_ROUTES } from "../../../../constants";
import { navigateToApp } from "../../../../hooks/use-auth-navigation";
import { useSession } from "../../../../session/use-session";
import { SignedInPanel } from "./components/signed-in-panel";
import { useSignOut } from "./composables/use-sign-out";
import { useSignedInDestinations } from "./composables/use-signed-in-destinations";

export function SignedInView() {
  const { status, session } = useSession();
  const navigate = useNavigate();
  const { loading, submit } = useSignOut();
  const { destinations } = useSignedInDestinations();

  useEffect(() => {
    if (status === "anonymous") {
      navigate(AUTH_ROUTES.login, { replace: true });
    }
  }, [status, navigate]);

  if (status !== "authenticated" || !session) {
    return null;
  }

  return (
    <SignedInPanel
      session={session}
      destinations={destinations}
      signingOut={loading}
      onNavigate={navigateToApp}
      onSignOut={() => {
        void submit();
      }}
    />
  );
}
