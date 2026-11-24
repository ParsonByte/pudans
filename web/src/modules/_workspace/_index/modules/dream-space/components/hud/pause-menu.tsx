"use client";

import { useRouter } from "next/navigation";

import { Play, RefreshCcw, RotateCcw, Sparkles } from "lucide-react";

import { INDEX_DREAM_SPACE_HREF } from "@/routes/(_workspace)/(_index)/paths";

import { SESSION_STORAGE_KEY } from "../../constants";
import { resetDreamWorldPosition, setDreamPaused, startNewDreamSession } from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { createSessionId } from "../../world/seed";

export function PauseMenu() {
    const paused = useDreamStore((state) => state.paused);
    const world = useDreamStore((state) => state.world);
    const router = useRouter();

    if (!paused) {
        return null;
    }

    const beginNewSession = () => {
        if (!world) {
            return;
        }

        const sessionId = createSessionId();
        window.sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
        startNewDreamSession(sessionId, world.userId);
        setDreamPaused(false);
        router.replace(INDEX_DREAM_SPACE_HREF);
    };

    return (
        <div className={styles.drawerBackdrop}>
            <section
                aria-label="梦境暂停菜单"
                className={`${styles.glassPanel} ${styles.sidePanel}`}
                style={{
                    left: "50%",
                    position: "absolute",
                    top: "50%",
                    transform: "translate(-50%, -50%)",
                }}
            >
                <div className={styles.panelHeader}>
                    <div>
                        <h2 className={styles.panelTitle}>梦境暂歇</h2>
                        <p className={styles.panelKicker}>WORLD PAUSED</p>
                    </div>
                </div>
                <div className={styles.panelBody}>
                    <p className={styles.narrative}>
                        世界仍停留在你离开的那一刻。继续行走，或让新的会话生成另一片天地。
                    </p>
                    <div className="mt-4 flex flex-col gap-2">
                        <button className={styles.primaryButton} onClick={() => setDreamPaused(false)} type="button">
                            <Play aria-hidden size={13} />
                            继续探索
                        </button>
                        <button
                            className={styles.secondaryButton}
                            onClick={() => {
                                resetDreamWorldPosition();
                                setDreamPaused(false);
                            }}
                            type="button"
                        >
                            <RotateCcw aria-hidden size={13} />
                            返回出生点
                        </button>
                        <button className={styles.secondaryButton} onClick={beginNewSession} type="button">
                            <RefreshCcw aria-hidden size={13} />
                            生成新会话天地
                        </button>
                    </div>
                    <p className={styles.portalHint}>
                        <Sparkles aria-hidden size={11} />
                        同一用户、同一会话会保留同一套地形、星辰与门户坐标。
                    </p>
                </div>
            </section>
        </div>
    );
}
