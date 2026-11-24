"use client";

import { Bookmark, RefreshCw } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import type { InspirationCard } from "../../types";
import { ConstellationGenerator } from "./constellation-generator";

export function InspirationCardDeck({
    card,
    onRefresh,
    onSave,
}: {
    card: InspirationCard;
    onRefresh: () => void;
    onSave: () => void;
}) {
    return (
        <article className={styles.inspirationCard}>
            <span className={styles.inspirationHeader}>CONSTELLATION PROMPT</span>
            <h3 className={styles.inspirationTitle}>{card.title}</h3>
            <div className={`${styles.inspirationKeywords} mt-2`}>
                {card.keywords.map((keyword) => (
                    <span className={`${styles.tag} ${styles.tagStatic}`} key={keyword}>
                        {keyword}
                    </span>
                ))}
            </div>
            <p className={styles.inspirationPrompt}>{card.prompt}</p>
            <ConstellationGenerator memoryIds={card.memoryIds} />
            <div className="mt-3 flex gap-2">
                <button className={styles.secondaryButton} onClick={onRefresh} type="button">
                    <RefreshCw aria-hidden size={12} />
                    换一批
                </button>
                <button
                    className={card.saved ? styles.primaryButton : styles.secondaryButton}
                    onClick={onSave}
                    type="button"
                >
                    <Bookmark aria-hidden size={12} />
                    {card.saved ? "已收藏" : "收藏"}
                </button>
            </div>
        </article>
    );
}
