"use client";

import styles from "../../styles/dream-space.module.css";

export function NPCDialogueBubble({ text }: { text: string }) {
    return <div className={styles.npcDialogue}>{text}</div>;
}
