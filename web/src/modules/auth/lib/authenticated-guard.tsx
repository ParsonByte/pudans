"use client";

import { type ReactNode, useEffect, useState } from "react";

import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { fetchNavigation } from "@/modules/workspace/lib/permission-api";

import { AuthenticatedShell } from "./authenticated-shell";
import { getLoginSession } from "./session";
import { type SessionContextValue, SessionProvider } from "./session-context";

export function AuthenticatedGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [context, setContext] = useState<SessionContextValue | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      const [session, navigation] = await Promise.all([
        getLoginSession().catch(() => null),
        fetchNavigation().catch(() => null),
      ]);

      if (cancelled) {
        return;
      }

      if (!session) {
        toast.error("无法获取登录状态，请稍后重试。");
        router.replace("/sign-in");
        return;
      }

      if (!session.authenticated) {
        const redirect = encodeURIComponent(window.location.href);
        router.replace(`/sign-in?redirect=${redirect}`);
        return;
      }

      if (session.consoleAccessDisallowed) {
        router.replace("/403");
        return;
      }

      if (!navigation) {
        toast.error("无法获取工作区导航，请稍后重试。");
        router.replace("/500");
        return;
      }

      setContext({ session, navigation });
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, [router]);

  if (!context) {
    return null;
  }

  return (
    <SessionProvider value={context}>
      <AuthenticatedShell session={context.session} navigation={context.navigation}>
        {children}
      </AuthenticatedShell>
    </SessionProvider>
  );
}
