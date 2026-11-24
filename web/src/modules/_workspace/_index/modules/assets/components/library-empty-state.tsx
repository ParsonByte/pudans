import type { ReactNode } from "react";

import { Button } from "tdesign-react";

import { LIBRARY_TONE_CLASS, type LibraryTone } from "../composables/_library-tone";
import styles from "../styles/assets-view.module.css";

type LibraryEmptyStateProps = {
    actionLabel?: string;
    children?: ReactNode;
    dashed?: boolean;
    description: string;
    icon: ReactNode;
    iconTone?: LibraryTone;
    onAction?: () => void;
    title: string;
};

export function LibraryEmptyState({
    actionLabel,
    children,
    dashed = false,
    description,
    icon,
    iconTone = "brand",
    onAction,
    title,
}: LibraryEmptyStateProps) {
    const hasExtra = children !== undefined && children !== null;

    return (
        <div
            className={`${styles.emptyCard} ${dashed ? styles.emptyCardDashed : ""} ${
                hasExtra ? styles.emptyCardSplit : ""
            }`}
        >
            <div className={styles.emptyMain}>
                <span className={`${styles.emptyIcon} ${LIBRARY_TONE_CLASS[iconTone]}`}>{icon}</span>
                <h3 className={styles.emptyTitle}>{title}</h3>
                <p className={styles.emptyDescription}>{description}</p>
                {hasExtra && !actionLabel ? (
                    <div aria-hidden className={styles.archiveFlow}>
                        <span className={styles.flowStep}>
                            <b>01</b>
                            系统生成
                        </span>
                        <span className={styles.flowConnector} />
                        <span className={styles.flowStep}>
                            <b>02</b>
                            自动识别
                        </span>
                        <span className={styles.flowConnector} />
                        <span className={styles.flowStep}>
                            <b>03</b>
                            归入资源库
                        </span>
                    </div>
                ) : null}
                {actionLabel && onAction ? (
                    <Button onClick={onAction} variant="outline">
                        {actionLabel}
                    </Button>
                ) : null}
            </div>
            {hasExtra ? <div className={styles.emptyExtra}>{children}</div> : null}
        </div>
    );
}
