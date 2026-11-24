"use client";

import { Move } from "lucide-react";

import styles from "../../styles/dream-space.module.css";

export function TouchLookArea() {
    return (
        <div aria-hidden className={styles.touchLook}>
            <span className="absolute top-1/2 right-5 flex -translate-y-1/2 items-center gap-1 text-[8px] text-white/26">
                <Move size={11} />
                拖动视角
            </span>
        </div>
    );
}
