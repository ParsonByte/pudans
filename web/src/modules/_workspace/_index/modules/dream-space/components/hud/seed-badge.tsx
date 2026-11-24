"use client";

import { Fingerprint } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { useDreamSeed } from "../core/dream-seed-provider";

export function SeedBadge() {
    const seed = useDreamSeed();

    return (
        <span className={styles.seedBadge} title={`世界种子 ${seed.seed}`}>
            <Fingerprint aria-hidden size={10} />
            SEED {seed.shortCode}
        </span>
    );
}
