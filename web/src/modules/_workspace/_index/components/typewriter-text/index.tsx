"use client";

import { useEffect, useState } from "react";

import { useMediaQuery } from "@/hooks/ui/use-media-query";

import styles from "../../styles/typewriter-text.module.css";

type TypewriterTextProps = {
    speed?: number;
    text: string;
};

export function TypewriterText({ speed = 55, text }: TypewriterTextProps) {
    const reduceMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
    const [typing, setTyping] = useState({ text, visibleLength: 0 });
    const visibleLength = typing.text === text ? typing.visibleLength : 0;
    const complete = reduceMotion || visibleLength >= text.length;
    const visibleText = reduceMotion ? text : text.slice(0, visibleLength);

    useEffect(() => {
        if (reduceMotion) {
            return;
        }

        let index = 0;
        const timer = window.setInterval(() => {
            index += 1;
            setTyping({ text, visibleLength: index });

            if (index >= text.length) {
                window.clearInterval(timer);
            }
        }, speed);

        return () => window.clearInterval(timer);
    }, [reduceMotion, speed, text]);

    return (
        <span className={styles.root}>
            <span className={styles.srOnly}>{text}</span>
            <span aria-hidden className={styles.measure}>
                {text}
            </span>
            <span aria-hidden className={styles.visible}>
                {visibleText}
                <span className={`${styles.cursor} ${complete ? styles.cursorComplete : ""}`} />
            </span>
        </span>
    );
}
