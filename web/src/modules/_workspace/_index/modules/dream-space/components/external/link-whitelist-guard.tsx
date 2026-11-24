"use client";

import type { ReactNode } from "react";

import { ShieldX } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { resolveExternalLink } from "../../world/external-link-service";

export function LinkWhitelistGuard({ children, url }: { children: ReactNode; url: string }) {
    const decision = resolveExternalLink(url);

    if (decision.allowed) {
        return <>{children}</>;
    }

    return (
        <div className={styles.errorPanel}>
            <ShieldX aria-hidden size={28} />
            <h1>门户地址被拦截</h1>
            <p>{decision.reason}。只有经注册的 HTTPS 域名可以离开幻境空间。</p>
        </div>
    );
}
