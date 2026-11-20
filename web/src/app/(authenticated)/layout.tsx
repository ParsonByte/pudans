import type { ReactNode } from "react";

import { AuthenticatedGuard } from "@/modules/auth/lib/authenticated-guard";

export default function AuthenticatedRouteLayout({ children }: { children: ReactNode }) {
  return <AuthenticatedGuard>{children}</AuthenticatedGuard>;
}
