"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";

import { usePathname, useSearchParams } from "next/navigation";

const START_PERCENT = 8;
const MAX_PERCENT = 90;
const FINISH_DELAY = 180;
const SAFETY_TIMEOUT = 10_000;

function shouldStartProgress(event: MouseEvent): boolean {
    if (
        event.defaultPrevented ||
        event.button !== 0 ||
        event.metaKey ||
        event.ctrlKey ||
        event.shiftKey ||
        event.altKey
    ) {
        return false;
    }

    const anchor = event.target instanceof Element ? event.target.closest("a") : null;

    if (!anchor || anchor.hasAttribute("download")) {
        return false;
    }

    const href = anchor.getAttribute("href");

    if (!href || href.startsWith("#") || (anchor.target && anchor.target !== "_self")) {
        return false;
    }

    const url = new URL(href, window.location.href);

    return (
        url.origin === window.location.origin &&
        !(url.pathname === window.location.pathname && url.search === window.location.search)
    );
}

function NavigationProgressBar() {
    const pathname = usePathname();
    const search = useSearchParams().toString();
    const [visible, setVisible] = useState(false);
    const [percent, setPercent] = useState(0);
    const visibleRef = useRef(false);
    const intervalRef = useRef<number | null>(null);
    const finishTimerRef = useRef<number | null>(null);
    const safetyTimerRef = useRef<number | null>(null);

    const clearTimers = useCallback(() => {
        if (intervalRef.current !== null) {
            window.clearInterval(intervalRef.current);
            intervalRef.current = null;
        }
        if (finishTimerRef.current !== null) {
            window.clearTimeout(finishTimerRef.current);
            finishTimerRef.current = null;
        }
        if (safetyTimerRef.current !== null) {
            window.clearTimeout(safetyTimerRef.current);
            safetyTimerRef.current = null;
        }
    }, []);

    const finish = useCallback(() => {
        clearTimers();

        if (!visibleRef.current) {
            return;
        }

        setPercent(100);
        finishTimerRef.current = window.setTimeout(() => {
            visibleRef.current = false;
            setVisible(false);
            setPercent(0);
            finishTimerRef.current = null;
        }, FINISH_DELAY);
    }, [clearTimers]);

    const start = useCallback(() => {
        clearTimers();
        visibleRef.current = true;
        setVisible(true);
        setPercent(START_PERCENT);
        intervalRef.current = window.setInterval(() => {
            setPercent((current) => (current >= MAX_PERCENT ? current : current + (MAX_PERCENT - current) * 0.12));
        }, 200);
        safetyTimerRef.current = window.setTimeout(finish, SAFETY_TIMEOUT);
    }, [clearTimers, finish]);

    useEffect(() => {
        finish();
    }, [finish, pathname, search]);

    useEffect(() => {
        const handleClick = (event: MouseEvent) => {
            if (shouldStartProgress(event)) {
                start();
            }
        };
        const handlePopState = () => start();

        document.addEventListener("click", handleClick);
        window.addEventListener("popstate", handlePopState);

        return () => {
            document.removeEventListener("click", handleClick);
            window.removeEventListener("popstate", handlePopState);
            clearTimers();
        };
    }, [clearTimers, start]);

    return (
        <div
            aria-hidden={!visible}
            aria-valuemax={100}
            aria-valuemin={0}
            aria-valuenow={percent}
            className="pointer-events-none fixed inset-x-0 top-0 z-[2000] h-0.5"
            role="progressbar"
        >
            <div
                className="h-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.8)] transition-[width,opacity] duration-200 ease-out dark:bg-blue-400"
                style={{ opacity: visible ? 1 : 0, width: `${percent}%` }}
            />
        </div>
    );
}

export function NavigationProgress() {
    return (
        <Suspense fallback={null}>
            <NavigationProgressBar />
        </Suspense>
    );
}
