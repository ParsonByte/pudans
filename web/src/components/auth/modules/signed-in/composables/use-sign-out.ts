import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AUTH_ROUTES } from "../../../../../constants";
import { useSession } from "../../../../../session/use-session";

export function useSignOut() {
  const { signOut } = useSession();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const submit = async (): Promise<void> => {
    setLoading(true);
    try {
      await signOut();
      navigate(AUTH_ROUTES.login, { replace: true });
    } finally {
      setLoading(false);
    }
  };

  return { loading, submit };
}
