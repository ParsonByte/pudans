"use client";

import { useEffect, useState } from "react";

import { Maximize2, Minimize2 } from "lucide-react";

import styles from "../../styles/dream-space.module.css";
import { isDocumentFullscreen, toggleDocumentFullscreen } from "../../world/fullscreen";

export function FullscreenToggle() {
    const [fullscreen, setFullscreen] = useState(false);

    useEffect(() => {
        const update = () => setFullscreen(isDocumentFullscreen());
        document.addEventListener("fullscreenchange", update);
        update();

        return () => document.removeEventListener("fullscreenchange", update);
    }, []);

    return (
        <button
            aria-label={fullscreen ? "退出全屏" : "进入全屏"}
            className={styles.secondaryButton}
            onClick={toggleDocumentFullscreen}
            type="button"
        >
            {fullscreen ? <Minimize2 aria-hidden size={13} /> : <Maximize2 aria-hidden size={13} />}
            {fullscreen ? "退出全屏" : "全屏"}
        </button>
    );
}
