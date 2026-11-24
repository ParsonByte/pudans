"use client";

import { Bell } from "lucide-react";

import { pushDreamToast, toggleDreamPanel } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function NotificationBell() {
    const discovered = useDreamStore((state) => state.discoveredMemoryIds.length);

    return (
        <button
            aria-label={`已发现 ${discovered} 条记忆`}
            className={styles.iconButton}
            onClick={() => {
                toggleDreamPanel("journal");
                pushDreamToast(`本会话已发现 ${discovered} 条记忆`, "info");
            }}
            type="button"
        >
            <Bell aria-hidden size={15} />
            {discovered > 0 ? <span className="absolute top-1 right-1 size-1.5 rounded-full bg-amber-300" /> : null}
        </button>
    );
}
