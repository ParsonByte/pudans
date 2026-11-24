"use client";

import { useState } from "react";

import styles from "../../styles/dream-space.module.css";

const CHOICES = [
    { answer: "我在找一段还没有名字的记忆。", reply: "那就先别给它命名。名字会收束光，而光需要继续游走。" },
    { answer: "这里是现实还是梦境？", reply: "你站的地方叫你此刻愿意相信的东西。" },
    { answer: "怎样才能离开？", reply: "找到那扇门，但别忘了先带走门后照进来的光。" },
] as const;

export function EchoDialogue() {
    const [reply, setReply] = useState("回声行者正在等待你开口。他身后的光比语言更早回答。");

    return (
        <div className={styles.dialoguePanel}>
            <span className={styles.projectionKicker}>ECHO WALKER</span>
            <p className={styles.narrative}>{reply}</p>
            <div className={styles.dialogueChoices}>
                {CHOICES.map((choice) => (
                    <button
                        className={styles.choiceButton}
                        key={choice.answer}
                        onClick={() => setReply(choice.reply)}
                        type="button"
                    >
                        {choice.answer}
                    </button>
                ))}
            </div>
        </div>
    );
}
