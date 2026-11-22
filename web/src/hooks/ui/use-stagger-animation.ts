"use client";

import { useEffect, useRef } from "react";

import { animate, stagger } from "motion";

export type StaggerAnimationOptions = {
    delay?: number;
    duration?: number;
    distance?: number;
};

export function useStaggerAnimation<T extends HTMLElement>(
    trigger: unknown,
    { delay = 0.05, duration = 0.4, distance = 16 }: StaggerAnimationOptions = {},
) {
    const ref = useRef<T>(null);

    useEffect(() => {
        const container = ref.current;
        if (!container) {
            return;
        }

        const children = Array.from(container.children);
        if (children.length === 0) {
            return;
        }

        const controls = animate(
            children,
            { opacity: [0, 1], transform: [`translateY(${distance}px)`, "translateY(0px)"] },
            { delay: stagger(delay), duration, ease: "easeOut" },
        );

        return () => controls.stop();
    }, [trigger, delay, duration, distance]);

    return ref;
}
