import type { ReactNode } from "react";

import { LIBRARY_TONE_CLASS, type LibraryTone } from "../composables/_library-tone";
import styles from "../styles/assets-view.module.css";

type LibraryHeaderProps = {
    description: string;
    icon: ReactNode;
    id: string;
    pendingLabel: string;
    title: string;
    tone: LibraryTone;
};

export function LibraryHeader({ description, icon, id, pendingLabel, title, tone }: LibraryHeaderProps) {
    return (
        <div className={styles.libraryHeading}>
            <span className={`${styles.libraryHeadingIcon} ${LIBRARY_TONE_CLASS[tone]}`} aria-hidden>
                {icon}
            </span>
            <div className={styles.libraryHeadingCopy}>
                <h2 id={id}>{title}</h2>
                <p>{description}</p>
            </div>
            <span className={styles.pendingBadge}>{pendingLabel}</span>
        </div>
    );
}
