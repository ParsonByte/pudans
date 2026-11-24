"use client";

import { ShieldCheck, ShieldX } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import type { PortalKind } from "../../types";

export function PortalPermissionHint({ kind, reason }: { kind: PortalKind; reason?: string }) {
    const external = kind === "external";
    const blocked = Boolean(reason);
    const Icon = blocked || !external ? ShieldCheck : ShieldX;

    return (
        <p className={styles.portalHint}>
            <Icon aria-hidden size={11} />
            {reason ??
                (external ? "仅 HTTPS 白名单域名可打开；默认不嵌入主画布。" : "区域门户只改变当前梦境，不会跳离页面。")}
        </p>
    );
}
