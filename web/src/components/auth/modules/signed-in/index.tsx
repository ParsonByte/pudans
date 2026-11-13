import { navigateToApp } from "../../../../hooks/use-auth-navigation";
import { useSession } from "../../../../session/use-session";
import { SignedInPanel } from "./components/signed-in-panel";
import { SignedOutPanel } from "./components/signed-out-panel";
import { useSignOut } from "./composables/use-sign-out";
import { useSignedInDestinations } from "./composables/use-signed-in-destinations";

export function SignedInView() {
  const { status, session } = useSession();
  const { loading, submit } = useSignOut();
  const { destinations } = useSignedInDestinations();

  if (status === "loading") {
    return null;
  }

  if (status === "anonymous" || !session) {
    return <SignedOutPanel />;
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
