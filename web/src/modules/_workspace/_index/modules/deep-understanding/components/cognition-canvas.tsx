import Image from "next/image";

import { LockOnIcon } from "tdesign-icons-react";

import { UNDERSTANDING_DIMENSIONS, type UnderstandingDimensionId } from "../composables/_dimensions";
import styles from "../styles/deep-understanding.module.css";

type CognitionCanvasProps = {
    activeDimensionId: UnderstandingDimensionId;
    onSelect: (id: UnderstandingDimensionId) => void;
};

export function CognitionCanvas({ activeDimensionId, onSelect }: CognitionCanvasProps) {
    return (
        <section aria-labelledby="cognition-canvas-title" className={styles.canvas}>
            <header className={styles.canvasHeader}>
                <div className={styles.canvasTitle}>
                    <span aria-hidden className={styles.liveDot} />
                    <span id="cognition-canvas-title">认知空间</span>
                    <span className={styles.canvasCode}>LIVE MAP</span>
                </div>
                <span className={styles.canvasScope}>
                    <LockOnIcon size={13} />
                    当前工作区
                </span>
            </header>

            <div className={styles.stage}>
                <div aria-hidden className={styles.stageGrid} />
                <div aria-hidden className={styles.stageGlow} />
                <div aria-hidden className={`${styles.orbitRing} ${styles.orbitOuter}`} />
                <div aria-hidden className={`${styles.orbitRing} ${styles.orbitMiddle}`} />
                <div aria-hidden className={`${styles.orbitRing} ${styles.orbitInner}`} />
                <div aria-hidden className={styles.coreHalo} />

                <svg aria-hidden className={styles.edges} preserveAspectRatio="none" viewBox="0 0 100 100">
                    {UNDERSTANDING_DIMENSIONS.map((dimension) => (
                        <line
                            className={`${styles.edge} ${dimension.id === activeDimensionId ? styles.edgeActive : ""}`}
                            key={dimension.id}
                            x1="50"
                            x2={dimension.x}
                            y1="50"
                            y2={dimension.y}
                        />
                    ))}
                </svg>

                <div className={styles.core}>
                    <Image
                        alt=""
                        aria-hidden
                        className={styles.coreMark}
                        height={32}
                        priority
                        src="/brand/icon-small.svg"
                        unoptimized
                        width={32}
                    />
                    <strong>DANS</strong>
                    <span>认知核心</span>
                </div>

                {UNDERSTANDING_DIMENSIONS.map((dimension) => {
                    const active = dimension.id === activeDimensionId;

                    return (
                        <button
                            aria-label={`查看${dimension.label}维度`}
                            aria-pressed={active}
                            className={`${styles.dimensionNode} ${active ? styles.dimensionNodeActive : ""}`}
                            key={dimension.id}
                            onClick={() => onSelect(dimension.id)}
                            style={{ left: `${dimension.x}%`, top: `${dimension.y}%` }}
                            type="button"
                        >
                            <span className={styles.nodeIcon}>{dimension.icon}</span>
                            <span>{dimension.label}</span>
                            <span aria-hidden className={styles.nodeSignal} />
                        </button>
                    );
                })}
            </div>

            <footer className={styles.canvasFooter}>
                <span className={styles.selectionHint}>
                    <span aria-hidden className={styles.selectionDot} />
                    选择维度查看理解边界
                </span>
                <span className={styles.awaitingBadge}>等待首次理解</span>
            </footer>
        </section>
    );
}
