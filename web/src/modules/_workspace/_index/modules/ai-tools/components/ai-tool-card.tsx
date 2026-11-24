import type { ReactNode } from "react";

import styles from "../styles/ai-tool-card.module.css";

export type AiToolCoverTone =
    "azure" | "clay" | "forest" | "mint" | "ocean" | "peach" | "rose" | "sand" | "sky" | "slate" | "steel";

type AiToolCardProps = {
    coverTone: AiToolCoverTone;
    description: string;
    icon: ReactNode;
    onSelect: () => void;
    title: string;
};

const COVER_TONE_CLASS: Record<AiToolCoverTone, string> = {
    azure: styles.coverAzure,
    clay: styles.coverClay,
    forest: styles.coverForest,
    mint: styles.coverMint,
    ocean: styles.coverOcean,
    peach: styles.coverPeach,
    rose: styles.coverRose,
    sand: styles.coverSand,
    sky: styles.coverSky,
    slate: styles.coverSlate,
    steel: styles.coverSteel,
};

export function AiToolCard({ coverTone, description, icon, onSelect, title }: AiToolCardProps) {
    return (
        <button
            aria-label={`${title}：${description}，接口待接入`}
            className={styles.card}
            onClick={onSelect}
            type="button"
        >
            <span aria-hidden className={`${styles.cover} ${COVER_TONE_CLASS[coverTone]}`}>
                <span className={styles.coverShape} />
                <span className={styles.coverFrame}>
                    <span className={styles.coverIcon}>{icon}</span>
                </span>
                <span className={styles.coverStatus}>预览待接入</span>
            </span>
            <span className={styles.body}>
                <span className={styles.title}>{title}</span>
                <span className={styles.description}>{description}</span>
            </span>
        </button>
    );
}
