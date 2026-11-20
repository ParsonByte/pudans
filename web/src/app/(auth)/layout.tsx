"use client";

import { type ReactNode, useEffect, useState } from "react";

import { usePathname, useRouter } from "next/navigation";

import { AuthLayout } from "@/modules/auth/auth-layout";
import { getLoginSession } from "@/modules/auth/lib/session";
import { resolveSetupRedirect } from "@/modules/setup/lib/setup-guard";

export default function AuthRouteLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const variant = pathname.startsWith("/legal") ? "legal" : "auth";

  useEffect(() => {
    let cancelled = false;

    const guard = async () => {
      const setupRedirect = await resolveSetupRedirect(pathname);

      if (cancelled) {
        return;
      }

      if (setupRedirect) {
        router.replace(setupRedirect);
        return;
      }

      if (pathname !== "/sign-in") {
        setReady(true);
        return;
      }

      const session = await getLoginSession().catch(() => null);

      if (cancelled) {
        return;
      }

      if (session?.authenticated && session.consoleAccessDisallowed) {
        router.replace("/403");
        return;
      }

      if (session?.authenticated) {
        router.replace("/");
        return;
      }

      setReady(true);
    };

    void guard();

    return () => {
      cancelled = true;
    };
  }, [pathname, router]);

  if (!ready) {
    return null;
  }

  return <AuthLayout variant={variant}>{children}</AuthLayout>;
}
