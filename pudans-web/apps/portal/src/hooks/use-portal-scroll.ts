"use client";

import { useEffect, useRef, type RefObject } from "react";

import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

export type PortalProgress = {
  current: number;
  target: number;
};

export function usePortalScroll(
  scrollRef: RefObject<HTMLDivElement | null>
): RefObject<PortalProgress> {
  const progressRef = useRef<PortalProgress>({ current: 0, target: 0 });

  useEffect(() => {
    const triggerElement = scrollRef.current;
    if (!triggerElement) {
      return;
    }

    gsap.registerPlugin(ScrollTrigger);
    const trigger = ScrollTrigger.create({
      start: "top top",
      end: "bottom bottom",
      trigger: triggerElement,
      onUpdate: (self) => {
        progressRef.current.target = self.progress;
      },
    });

    const refresh = () => {
      ScrollTrigger.refresh();
    };
    const refreshTimer = window.setTimeout(refresh, 120);

    window.addEventListener("resize", refresh);
    document.fonts?.ready.then(refresh).catch(() => undefined);

    return () => {
      window.clearTimeout(refreshTimer);
      window.removeEventListener("resize", refresh);
      trigger.kill();
      progressRef.current.target = 0;
      progressRef.current.current = 0;
    };
  }, [scrollRef]);

  return progressRef;
}