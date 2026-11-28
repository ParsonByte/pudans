"use client";

import { useRef } from "react";

import { usePortalScene } from "@/hooks/use-portal-scene";
import { usePortalScroll } from "@/hooks/use-portal-scroll";

import { PortalBackground } from "./portal-background";
import { PortalCaption } from "./portal-caption";
import { PortalFinale } from "./portal-finale";
import { PortalHero } from "./portal-hero";
import { PortalHint } from "./portal-hint";
import { PortalLoading } from "./portal-loading";
import { PortalMeter } from "./portal-meter";

import "./portal-experience.modules.less";

export function PortalExperience() {
  const rootRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const progressRef = usePortalScroll(scrollRef);

  usePortalScene({ canvasRef, progressRef, rootRef });

  return (
    <div className="portal-experience" ref={rootRef}>
      <PortalBackground />
      <canvas className="portal-scene" ref={canvasRef} />
      <div className="portal-overlay">
        <PortalHero />
        <PortalCaption />
        <PortalMeter />
        <PortalHint />
        <PortalFinale />
        <PortalLoading />
      </div>
      <div className="portal-scroll-space" ref={scrollRef} />
    </div>
  );
}