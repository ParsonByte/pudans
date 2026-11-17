import { Icon } from "@iconify/react";

import { IconFeishu } from "@/assets/custom/icon-feishu";
import { cn } from "@/lib/utils";

export type IdentityKind = "totp" | "oauth2" | "oidc" | "feishu" | "pat";

type IdentityIconProps = {
  kind: IdentityKind;
  size?: number;
  className?: string;
};

const ICONS: Record<Exclude<IdentityKind, "feishu">, string> = {
  totp: "mdi:two-factor-authentication",
  oauth2: "logos:oauth",
  oidc: "simple-icons:openid",
  pat: "mdi:key-variant",
};

export function IdentityIcon({ kind, size = 112, className }: IdentityIconProps) {
  if (kind === "feishu") {
    return <IconFeishu size={size} className={cn("shrink-0", className)} />;
  }
  return (
    <Icon
      icon={ICONS[kind]}
      width={size}
      height={size}
      className={cn("shrink-0", className)}
      aria-hidden="true"
    />
  );
}
