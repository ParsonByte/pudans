import { type CSSProperties, Fragment, useEffect, useRef, useState } from "react";

import Image from "next/image";

import { LockOnIcon } from "tdesign-icons-react";

import { UNDERSTANDING_DIMENSIONS } from "../composables/_dimensions";
import styles from "../styles/deep-understanding.module.css";

const RADAR_SPOKE_ANGLES = [0, 45, 90, 135, 180, 225, 270, 315] as const;
const INFLOW_ORBS = [0, 1, 2] as const;

export function CognitionCanvas() {
    const stageRef = useRef<HTMLDivElement>(null);
    const [stageSize, setStageSize] = useState({ height: 0, width: 0 });

    useEffect(() => {
        const stage = stageRef.current;

        if (!stage) {
            return;
        }

        const syncStageSize = () => setStageSize({ height: stage.clientHeight, width: stage.clientWidth });
        const observer = new ResizeObserver(syncStageSize);

        syncStageSize();
        observer.observe(stage);

        return () => observer.disconnect();
    }, []);

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

            <div className={styles.stage} ref={stageRef}>
                <div aria-hidden className={styles.stageGrid} />
                <div aria-hidden className={styles.stageGlow} />
                <div className={styles.radarField}>
                    <div aria-hidden className={`${styles.orbitRing} ${styles.orbitOuter}`} />
                    <div aria-hidden className={`${styles.orbitRing} ${styles.orbitMiddle}`} />
                    <div aria-hidden className={`${styles.orbitRing} ${styles.orbitInner}`} />
                    <div aria-hidden className={styles.coreHalo} />
                </div>

                <svg aria-hidden className={styles.edges} preserveAspectRatio="none" viewBox="0 0 100 100">
                    {RADAR_SPOKE_ANGLES.map((angle) => (
                        <line
                            className={styles.spoke}
                            key={angle}
                            transform={`rotate(${angle} 50 50)`}
                            x1="50"
                            x2="50"
                            y1="50"
                            y2="4"
                        />
                    ))}
                    {UNDERSTANDING_DIMENSIONS.map((dimension) => (
                        <line
                            className={styles.edge}
                            key={dimension.id}
                            x1="50"
                            x2={dimension.x}
                            y1="50"
                            y2={dimension.y}
                        />
                    ))}
                </svg>

                <div aria-hidden className={styles.edgeOrbs}>
                    {stageSize.width > 0
                        ? UNDERSTANDING_DIMENSIONS.map((dimension, dimensionIndex) => {
                              const startX = (dimension.x / 100) * stageSize.width;
                              const startY = (dimension.y / 100) * stageSize.height;
                              const deltaX = stageSize.width / 2 - startX;
                              const deltaY = stageSize.height / 2 - startY;
                              const orbStyle = (delay: number) =>
                                  ({
                                      animationDelay: `${delay}s`,
                                      left: `${startX}px`,
                                      top: `${startY}px`,
                                      "--orb-x": `${deltaX}px`,
                                      "--orb-y": `${deltaY}px`,
                                  }) as CSSProperties;

                              return (
                                  <Fragment key={dimension.id}>
                                      {INFLOW_ORBS.map((orb) => (
                                          <span
                                              className={styles.edgeOrb}
                                              key={orb}
                                              style={orbStyle(orb * -0.92 + dimensionIndex * -0.18)}
                                          />
                                      ))}
                                      <span
                                          className={`${styles.edgeOrb} ${styles.edgeOrbReturn}`}
                                          style={orbStyle(-1.45 + dimensionIndex * -0.22)}
                                      />
                                  </Fragment>
                              );
                          })
                        : null}
                </div>

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

                <div aria-label="认知维度" className={styles.dimensionNodes} role="list">
                    {UNDERSTANDING_DIMENSIONS.map((dimension) => (
                        <div
                            className={styles.dimensionNode}
                            key={dimension.id}
                            role="listitem"
                            style={{ left: `${dimension.x}%`, top: `${dimension.y}%` }}
                        >
                            <span className={styles.nodeIcon}>{dimension.icon}</span>
                            <span>{dimension.label}</span>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
}
