"use client";

import { useEffect, useRef } from "react";

import { animate } from "motion";

import { useIntersectionObserver } from "@/hooks/utils/use-intersection-observer";

export type ScrollRevealOptions = {
    threshold?: number;
    rootMargin?: string;
    once?: boolean;
    duration?: number;
    distance?: number;
};

export function useScrollReveal<T extends HTMLElement>({
    threshold = 0.2,
    rootMargin = "0px",
    once = true,
    duration = 0.5,
    distance = 24,
}: ScrollRevealOptions = {}) {
    const { ref, isIntersecting } = useIntersectionObserver<T>({
        threshold,
        rootMargin,
        freezeOnceVisible: once,
    });
    const animatedRef = useRef(false);

    useEffect(() => {
        const element = ref.current;
        if (!element || !isIntersecting || (once && animatedRef.current)) {
            return;
        }

        animatedRef.current = true;
        const controls = animate(
            element,
            { opacity: [0, 1], transform: [`translateY(${distance}px)`, "translateY(0px)"] },
            { duration, ease: "easeOut" },
        );

        return () => controls.stop();
    }, [ref, isIntersecting, once, duration, distance]);

    return { ref, isVisible: isIntersecting };
}
