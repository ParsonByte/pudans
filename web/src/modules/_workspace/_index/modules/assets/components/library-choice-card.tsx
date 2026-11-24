import type { ReactElement } from "react";

import { LIBRARY_TONE_CLASS, type LibraryTone } from "../composables/_library-tone";
import styles from "../styles/assets-view.module.css";

type LibraryChoiceCardProps = {
    description: string;
    icon: ReactElement;
    label: string;
    tone: LibraryTone;
};

export function LibraryChoiceCard({ description, icon, label, tone }: LibraryChoiceCardProps) {
    return (
        <div className={styles.choiceCard}>
            <span className={`${styles.choiceIcon} ${LIBRARY_TONE_CLASS[tone]}`}>{icon}</span>
            <span className={styles.choiceCopy}>
                <strong>{label}</strong>
                <small>{description}</small>
            </span>
        </div>
    );
}
