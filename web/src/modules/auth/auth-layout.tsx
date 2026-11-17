import { useEffect, useRef, useState } from "react";

import { Link, Outlet, useLocation } from "@tanstack/react-router";
import { Pause, Play } from "lucide-react";

import { ThemeSwitch } from "@/components/theme-switch";
import { cn } from "@/lib/utils";

const BRAND_NAME = "PUDANS";
const BRAND_SUBTITLE = "身份与权限";

export function AuthLayout() {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [videoPlaying, setVideoPlaying] = useState(false);
  const isLegal = useLocation({
    select: (location) => location.pathname.startsWith("/legal"),
  });

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
        <source src={`${import.meta.env.BASE_URL}login-bg.mp4`} type="video/mp4" />
      </video>
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 z-[1] bg-[linear-gradient(90deg,rgba(0,0,0,0.78)_0%,rgba(0,0,0,0.46)_42%,rgba(0,0,0,0.2)_68%,rgba(0,0,0,0.38)_100%)] backdrop-blur-[2px]"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 z-[1] bg-[linear-gradient(180deg,rgba(0,0,0,0.34)_0%,transparent_28%,transparent_72%,rgba(0,0,0,0.38)_100%)]"
      />

      <header className="fixed inset-x-0 top-0 z-20 flex items-center justify-between gap-5 bg-gradient-to-b from-black/35 to-transparent px-5 py-4 text-white sm:px-10 sm:py-5">
        <Link
          to="/sign-in"
          aria-label={BRAND_NAME}
          className="inline-flex min-w-0 items-center gap-2.5"
        >
          <span className="inline-flex size-9 items-center justify-center rounded-lg border border-white/20 bg-white/10 text-base font-bold tracking-[-0.04em] backdrop-blur-md">
            P
          </span>
          <span className="flex min-w-0 flex-col gap-0.5 leading-none">
            <strong className="truncate text-sm font-bold tracking-[0.16em]">{BRAND_NAME}</strong>
            <small className="hidden text-[11px] font-medium tracking-[0.14em] text-white/55 sm:block">
              {BRAND_SUBTITLE}
            </small>
          </span>
        </Link>

        <div className="flex items-center justify-end gap-1">
          <ThemeSwitch className="text-white/70 hover:bg-white/10 hover:text-white" />
        </div>
      </header>

      {!isLegal ? (
        <section className="pointer-events-none fixed bottom-[clamp(88px,12vh,132px)] left-[clamp(28px,6vw,104px)] z-10 hidden max-w-[min(620px,48vw)] text-white drop-shadow-[0_4px_30px_rgba(0,0,0,0.55)] md:block">
          <p className="mb-4 text-xs font-semibold tracking-[0.22em] text-white/55">
            PUDANS · 数字身份
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
          isLegal ? "md:justify-center" : "md:justify-end",
        )}
      >
        <div
          className={cn(
            "flex w-full flex-col rounded-xl border border-border/80 bg-card p-6 text-card-foreground shadow-2xl md:rounded-2xl md:p-8",
            isLegal ? "max-w-[min(800px,calc(100vw-2rem))] md:min-h-[640px]" : "max-w-[460px]",
          )}
        >
          <Outlet />
        </div>
      </section>

      <button
        type="button"
        className={cn(
          "fixed bottom-20 left-1/2 z-20 hidden -translate-x-1/2 items-center gap-2 rounded-full border border-white/20 bg-white/10 px-3 py-1.5 text-xs font-medium whitespace-nowrap text-white/65 backdrop-blur-md transition-colors hover:bg-white/15 hover:text-white focus-visible:ring-2 focus-visible:ring-white/50 focus-visible:outline-none md:inline-flex",
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

      <footer className="fixed inset-x-0 bottom-0 z-20 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 bg-gradient-to-t from-black/40 to-transparent px-4 py-4 text-[11px] text-white/45">
        <span>
          © {new Date().getFullYear()} {BRAND_NAME}。保留所有权利。
        </span>
        <Link to="/legal" search={{ tab: "terms" }} className="transition-colors hover:text-white">
          用户协议
        </Link>
        <Link
          to="/legal"
          search={{ tab: "privacy" }}
          className="transition-colors hover:text-white"
        >
          隐私政策
        </Link>
        <Link
          to="/legal"
          search={{ tab: "confidentiality" }}
          className="transition-colors hover:text-white"
        >
          数据保密协议
        </Link>
      </footer>
    </main>
  );
}
