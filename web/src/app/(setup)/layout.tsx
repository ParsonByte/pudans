"use client";

import { type ReactNode, useEffect, useState } from "react";

import { usePathname, useRouter } from "next/navigation";

import { AuthLayout } from "@/modules/auth/auth-layout";
import { resolveSetupRedirect } from "@/modules/setup/lib/setup-guard";

export default function SetupRouteLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const guard = async () => {
      const redirect = await resolveSetupRedirect(pathname);

      if (cancelled) {
        return;
      }

      if (redirect) {
        router.replace(redirect);
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

  return <AuthLayout variant="setup">{children}</AuthLayout>;
}
