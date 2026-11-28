"use client";

import { useEffect, type RefObject } from "react";

import { createPortalScene } from "@/lib/portal/scene";

import type { PortalProgress } from "./use-portal-scroll";

type UsePortalSceneOptions = {
  canvasRef: RefObject<HTMLCanvasElement | null>;
  progressRef: RefObject<PortalProgress>;
  rootRef: RefObject<HTMLDivElement | null>;
};

export function usePortalScene({ canvasRef, progressRef, rootRef }: UsePortalSceneOptions) {
  useEffect(() => {
    const canvas = canvasRef.current;
    const root = rootRef.current;
    if (!canvas || !root) {
      return;
    }

    const scene = createPortalScene({ canvas, progressRef, root });
    scene.start();

    return () => {
      scene.dispose();
    };
  }, [canvasRef, progressRef, rootRef]);
}