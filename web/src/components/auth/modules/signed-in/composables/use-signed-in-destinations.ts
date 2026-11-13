import { isRoutePermitted } from "@pudans/shared";
import { useRedirectUri } from "../../../../../hooks/use-auth-navigation";
import { useSession } from "../../../../../session/use-session";

export interface SignedInDestination {
  key: "console" | "uc" | "redirect";
  href: string;
  labelKey: string;
  icon: string;
  primary: boolean;
}

export interface SignedInDestinations {
  destinations: SignedInDestination[];
  redirectUri: string | null;
}

/**
 * Buttons shown on the signed-in landing page.
 *
 * - a whitelisted, permitted redirect_uri becomes the single primary action;
 * - a disallowed-console account only ever sees the user centre;
 * - otherwise the console is primary and the user centre secondary.
 */
export function useSignedInDestinations(): SignedInDestinations {
  const redirectUri = useRedirectUri();
  const { consoleAccessDisallowed, hasPermission } = useSession();

  if (consoleAccessDisallowed) {
    const target =
      redirectUri?.startsWith("/uc") && isRoutePermitted(redirectUri, hasPermission)
        ? redirectUri
        : "/uc";
    return {
      redirectUri,
      destinations: [
        { key: "uc", href: target, labelKey: "enterUc", icon: "mdi:account-circle-outline", primary: true },
      ],
    };
  }

  if (redirectUri && isRoutePermitted(redirectUri, hasPermission)) {
    return {
      redirectUri,
      destinations: [
        {
          key: "redirect",
          href: redirectUri,
          labelKey: redirectUri.startsWith("/uc") ? "enterUc" : "enterConsole",
          icon: redirectUri.startsWith("/uc") ? "mdi:account-circle-outline" : "mdi:view-dashboard-outline",
          primary: true,
        },
      ],
    };
  }

  return {
    redirectUri,
    destinations: [
      { key: "console", href: "/console", labelKey: "enterConsole", icon: "mdi:view-dashboard-outline", primary: true },
      { key: "uc", href: "/uc", labelKey: "enterUc", icon: "mdi:account-circle-outline", primary: false },
    ],
  };
}
