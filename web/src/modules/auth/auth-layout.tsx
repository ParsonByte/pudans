"use client";

import { useEffect, useRef, useState } from "react";

import { Pause, Play } from "lucide-react";
import Link from "next/link";

import { PageTransition } from "@/components/motion/page-transition";
import { ThemeSwitch } from "@/components/theme-switch";
import { BASE_PATH } from "@/lib/base-path";
import { cn } from "@/lib/utils";

const BRAND_NAME = "PUDANS";

type AuthLayoutVariant = "auth" | "legal" | "setup";

type AuthLayoutProps = {
  variant?: AuthLayoutVariant;
  children: React.ReactNode;
};

export function AuthLayout({ variant = "auth", children }: AuthLayoutProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [videoPlaying, setVideoPlaying] = useState(false);
  const isLegal = variant === "legal";
  const isSetup = variant === "setup";
  const isCentered = isLegal || isSetup;

  useEffect(() => {
    const video = videoRef.current;
    if (video) {
      setVideoPlaying(!video.paused);
    }
  }, []);

  const toggleVideo = () => {
    const video = videoRef.current;
    if (!video) {
      return;
    }

    if (video.paused) {
      void video.play();
      setVideoPlaying(true);
    } else {
      video.pause();
      setVideoPlaying(false);
    }
  };

  return (
    <main className="relative isolate min-h-svh overflow-x-hidden bg-background text-foreground">
      <video
        ref={videoRef}
        className="pointer-events-none fixed inset-0 z-0 size-full object-cover object-[68%_center]"
        autoPlay
        muted
        loop
        playsInline
        preload="auto"
        disablePictureInPicture
        aria-hidden="true"
        tabIndex={-1}
        onPlay={() => setVideoPlaying(true)}
        onPause={() => setVideoPlaying(false)}
      >
        <source src={`${BASE_PATH}login-bg.mp4`} type="video/mp4" />
      </video>
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 z-[1] bg-[image:var(--auth-horizontal-overlay)] backdrop-blur-[2px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 z-[1] bg-[image:var(--auth-vertical-overlay)]"
      />

      <header className="fixed inset-x-0 top-0 z-20 flex items-center justify-between gap-5 bg-[image:var(--auth-header-gradient)] px-5 py-4 text-(--auth-foreground) sm:px-10 sm:py-5">
        <Link
          href={isSetup ? "/setup" : "/sign-in"}
          aria-label={BRAND_NAME}
          className="inline-flex min-w-0 items-center gap-2.5"
        >
          <img src={`${BASE_PATH}brand/icon-small.svg`} alt="" className="size-8 shrink-0" />
          <span className="truncate text-sm leading-none font-bold tracking-[0.16em]">
            {BRAND_NAME}
          </span>
        </Link>

        <div className="flex items-center justify-end gap-1">
          <ThemeSwitch className="text-(--auth-foreground-muted) hover:bg-(--auth-surface) hover:text-(--auth-foreground)" />
        </div>
      </header>

      {variant === "auth" ? (
        <section className="pointer-events-none fixed bottom-[clamp(88px,12vh,132px)] left-[clamp(28px,6vw,104px)] z-10 hidden max-w-[min(620px,48vw)] text-(--auth-foreground) [filter:drop-shadow(var(--auth-drop-shadow))] md:block">
          <p className="mb-4 text-xs font-semibold tracking-[0.22em] text-(--auth-foreground-muted)">
            PUDANS · 数字空间
          </p>
          <h1 className="flex flex-col gap-0.5 text-[clamp(32px,4.1vw,58px)] leading-[1.16] font-semibold tracking-[-0.045em]">
            <span>让每一个灵感，</span>
            <span>成为看得见的作品。</span>
          </h1>
        </section>
      ) : null}

      <section
        className={cn(
          "relative z-10 flex min-h-svh items-start justify-center px-4 pt-24 pb-28 md:items-center md:px-[clamp(24px,7vw,112px)] md:pt-28 md:pb-24",
          isCentered ? "md:justify-center" : "md:justify-end",
        )}
      >
        <div
          className={cn(
            "flex w-full flex-col border border-border/80 bg-card p-6 text-card-foreground shadow-2xl md:p-8",
            isLegal
              ? "max-w-[min(800px,calc(100vw-2rem))] md:min-h-[640px]"
              : isSetup
                ? "max-w-[min(720px,calc(100vw-2rem))]"
                : "max-w-[460px]",
          )}
        >
          <PageTransition className="flex-1">{children}</PageTransition>
        </div>
      </section>

      <button
        type="button"
        className={cn(
          "fixed bottom-20 left-1/2 z-20 hidden -translate-x-1/2 items-center gap-2 border border-(--auth-border) bg-(--auth-surface) px-3 py-1.5 text-xs font-medium whitespace-nowrap text-(--auth-foreground-subtle) backdrop-blur-md transition-colors hover:bg-(--auth-surface-hover) hover:text-(--auth-foreground) focus-visible:ring-2 focus-visible:ring-(--auth-ring) focus-visible:outline-none md:inline-flex",
        )}
        aria-label={videoPlaying ? "暂停背景视频" : "继续播放"}
        aria-pressed={!videoPlaying}
        onClick={toggleVideo}
      >
        {videoPlaying ? (
          <Pause className="size-3.5 text-primary" />
        ) : (
          <Play className="size-3.5 text-primary" />
        )}
        {videoPlaying ? "创意正在进行" : "开始发挥创意"}
      </button>

      <footer className="fixed inset-x-0 bottom-0 z-20 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 bg-[image:var(--auth-footer-gradient)] px-4 py-4 text-[11px] text-(--auth-foreground-subtle)">
        <span>
          © {new Date().getFullYear()} {BRAND_NAME}。保留所有权利。
        </span>
        {!isSetup ? (
          <>
            <Link
              href="/legal?tab=terms"
              className="transition-colors hover:text-(--auth-foreground)"
            >
              用户协议
            </Link>
            <Link
              href="/legal?tab=privacy"
              className="transition-colors hover:text-(--auth-foreground)"
            >
              隐私政策
            </Link>
            <Link
              href="/legal?tab=confidentiality"
              className="transition-colors hover:text-(--auth-foreground)"
            >
              数据保密协议
            </Link>
          </>
        ) : null}
      </footer>
    </main>
  );
}
