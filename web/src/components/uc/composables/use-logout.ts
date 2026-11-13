import { useNavigate } from "react-router-dom";
import { useSession } from "../../../session/use-session";

export function useLogout() {
  const { signOut } = useSession();
  const navigate = useNavigate();
  return async (): Promise<void> => {
    await signOut();
    navigate("/auth/", { replace: true });
  };
}
