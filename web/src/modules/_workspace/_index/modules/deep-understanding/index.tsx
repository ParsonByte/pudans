"use client";

import { ScanIcon } from "tdesign-icons-react";

import { CognitionCanvas } from "./components/cognition-canvas";
import { FloatingUnderstandingAssistant } from "./components/floating-understanding-assistant";
import styles from "./styles/deep-understanding.module.css";

export function DeepUnderstandingView() {
    return (
        <section aria-labelledby="deep-understanding-page-title" className={styles.page}>
            <div className={styles.scroller}>
                <div className={styles.content}>
                    <header className={styles.hero}>
                        <div className={styles.heroCopy}>
                            <span className={styles.eyebrow}>DANS · COGNITIVE LAYER</span>
                            <div className={styles.titleRow}>
                                <h1 className={styles.title} id="deep-understanding-page-title">
                                    深度理解
                                </h1>
                                <span className={styles.hubTag}>认知中枢</span>
                            </div>
                            <p className={styles.description}>让 DANS 不只看见内容，也理解你的意图、偏好与长期目标。</p>
                        </div>
                        <div aria-label="理解引擎状态" className={styles.engineStatus} role="status">
                            <span className={styles.engineGlyph}>
                                <ScanIcon size={18} />
                            </span>
                            <span className={styles.engineCopy}>
                                <strong>理解引擎</strong>
                                <small>接口待接入</small>
                            </span>
                            <span aria-hidden className={styles.engineSignal} />
                        </div>
                    </header>

                    <div className={styles.workspace}>
                        <CognitionCanvas />
                    </div>
                </div>
            </div>

            <FloatingUnderstandingAssistant />
        </section>
    );
}
