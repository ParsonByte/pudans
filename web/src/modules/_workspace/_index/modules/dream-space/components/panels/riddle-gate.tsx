"use client";

import { useState } from "react";

import { Check, LockKeyhole } from "lucide-react";

import styles from "../../styles/dream-space.module.css";

const OPTIONS = ["让星辰照亮大地", "把所有记忆收进抽屉", "关闭所有门户"] as const;

export function RiddleGate() {
    const [answer, setAnswer] = useState<string | null>(null);
    const solved = answer === OPTIONS[0];

    return (
        <div className={styles.riddlePanel}>
            <span className={styles.projectionKicker}>RIDDLE GATE</span>
            <p className={styles.narrative}>石碑只有一句残缺的话：梦境的意义，不是收藏，而是____。</p>
            <div className={styles.riddleOptions}>
                {OPTIONS.map((option) => (
                    <button
                        className={styles.choiceButton}
                        key={option}
                        onClick={() => setAnswer(option)}
                        type="button"
                    >
                        {option === answer && solved ? (
                            <Check aria-hidden size={10} />
                        ) : (
                            <LockKeyhole aria-hidden size={10} />
                        )}
                        {option}
                    </button>
                ))}
            </div>
            {answer ? (
                <p className={styles.portalHint}>
                    {solved ? "石碑亮起，远处一道星轨完成了闭环。" : "石碑没有回应。它似乎在等待更接近光的答案。"}
                </p>
            ) : null}
        </div>
    );
}
