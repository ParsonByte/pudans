"use client";

import { type ReactNode, useEffect, useState } from "react";

import { usePathname, useRouter } from "next/navigation";

import { useSessionContext } from "@/modules/auth/lib/session-context";
import { recordNavigation } from "@/modules/workspace/lib/navigation-audit";
import { isPathAccessible } from "@/modules/workspace/lib/workspaces";

export default function WorkspaceLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { navigation } = useSessionContext();
  const accessible = isPathAccessible(navigation, pathname);
  const [readyPath, setReadyPath] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const enter = async () => {
      if (!accessible) {
        router.replace("/403");
        return;
      }

      await recordNavigation(pathname);

      if (!cancelled) {
        setReadyPath(pathname);
      }
    };

    void enter();

    return () => {
      cancelled = true;
    };
  }, [accessible, pathname, router]);

  if (!accessible || readyPath !== pathname) {
    return null;
  }

  return children;
}
