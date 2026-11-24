"use client";

import type { ReactNode } from "react";

import { useConsoleSession } from "@/modules/_workspace/_console/composables/_session";

import styles from "../../styles/dream-space.module.css";
import { ReturnToDreamButton } from "./return-to-dream-button";

export function ExternalSessionGuard({ children }: { children: ReactNode }) {
    const session = useConsoleSession();

    if (session.isError) {
        return (
            <div className={styles.errorPanel}>
                <h1>无法确认梦境身份</h1>
                <p>外部门户依赖当前登录会话。请返回幻境空间后重新进入。</p>
                <ReturnToDreamButton />
            </div>
        );
    }

    return <>{children}</>;
}
