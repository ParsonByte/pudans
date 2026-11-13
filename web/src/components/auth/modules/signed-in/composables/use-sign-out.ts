import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AUTH_ROUTES } from "../../../../../constants";
import { beginLogout, endLogout } from "../../../../../session/logout-state";
import { useSession } from "../../../../../session/use-session";

export function useSignOut() {
  const { signOut } = useSession();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const submit = async (): Promise<void> => {
    setLoading(true);
    beginLogout();
    try {
      await signOut();
      navigate(AUTH_ROUTES.signedIn, { replace: true });
      endLogout();
    } finally {
      setLoading(false);
    }
  };

  return { loading, submit };
}
