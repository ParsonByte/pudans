"use client";

import { Component, type ErrorInfo, type ReactNode } from "react";

import { ShieldAlert } from "lucide-react";

import { INDEX_DREAM_SPACE_HREF } from "@/routes/(_workspace)/(_index)/paths";

import styles from "../../styles/dream-space.module.css";

type ExternalErrorBoundaryState = {
    failed: boolean;
};

export class ExternalErrorBoundary extends Component<{ children: ReactNode }, ExternalErrorBoundaryState> {
    state: ExternalErrorBoundaryState = { failed: false };

    static getDerivedStateFromError(): ExternalErrorBoundaryState {
        return { failed: true };
    }

    componentDidCatch(error: Error, info: ErrorInfo): void {
        console.error("[DANS External]", error, info.componentStack);
    }

    render() {
        if (!this.state.failed) {
            return this.props.children;
        }

        return (
            <div className={styles.errorPanel}>
                <ShieldAlert aria-hidden size={30} />
                <h1>外部页面暂时无法展开</h1>
                <p>门户容器出现异常。你可以安全返回幻境，梦境状态不会被清除。</p>
                <a className={styles.primaryButton} href={INDEX_DREAM_SPACE_HREF}>
                    返回幻境
                </a>
            </div>
        );
    }
}
