"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";

import Image from "next/image";
import Link from "next/link";

import { Button, Card, Dropdown } from "antd";
import { Moon, Pause, Play, Sun } from "lucide-react";
import { useTheme } from "next-themes";

const BRAND_NAME = "PUDANS";

type AuthLayoutProps = {
    children: ReactNode;
};

const THEME_ITEMS = [
    { key: "light", label: "浅色" },
    { key: "dark", label: "深色" },
    { key: "system", label: "跟随系统" },
];

function ThemeMenu() {
    const { theme, setTheme } = useTheme();

    return (
        <Dropdown
            menu={{
                items: THEME_ITEMS,
                onClick: ({ key }) => setTheme(key),
                selectable: true,
                selectedKeys: [theme ?? "system"],
            }}
            trigger={["click"]}
        >
            <Button
                aria-label="切换主题"
                className="relative text-(--auth-foreground-muted) hover:bg-(--auth-surface) hover:text-(--auth-foreground)"
                type="text"
            >
                <Sun className="size-[1.2rem] scale-100 rotate-0 transition-all dark:scale-0 dark:-rotate-90" />
                <Moon className="absolute size-[1.2rem] scale-0 rotate-90 transition-all dark:scale-100 dark:rotate-0" />
            </Button>
        </Dropdown>
    );
}

export function AuthLayout({ children }: AuthLayoutProps) {
    const videoRef = useRef<HTMLVideoElement>(null);
    const [videoPlaying, setVideoPlaying] = useState(false);

    useEffect(() => {
        window.scrollTo(0, 0);

        const video = videoRef.current;

        if (video) {
            void video.play().catch(() => undefined);
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
        } else {
            video.pause();
        }
    };

    return (
        <main className="relative isolate min-h-dvh overflow-x-hidden bg-neutral-950 text-(--auth-foreground)">
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
                <source src="/login-bg.mp4" type="video/mp4" />
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
                <Link href="/sign-in" aria-label={BRAND_NAME} className="inline-flex min-w-0 items-center gap-2.5">
                    <Image alt="" className="shrink-0" height={32} src="/brand/icon-small.svg" unoptimized width={32} />
                    <span className="truncate text-sm leading-none font-bold tracking-[0.16em]">{BRAND_NAME}</span>
                </Link>
                <ThemeMenu />
            </header>

            <section className="pointer-events-none fixed bottom-[clamp(88px,12vh,132px)] left-[clamp(28px,6vw,104px)] z-10 hidden max-w-[min(620px,48vw)] text-(--auth-foreground) [filter:drop-shadow(var(--auth-drop-shadow))] md:block">
                <p className="mb-4 text-xs font-semibold tracking-[0.22em] text-(--auth-foreground-muted)">
                    PUDANS · 数字空间
                </p>
                <h1 className="flex flex-col gap-0.5 text-[clamp(32px,4.1vw,58px)] leading-[1.16] font-semibold tracking-[-0.045em]">
                    <span>让每一个灵感，</span>
                    <span>成为看得见的作品。</span>
                </h1>
            </section>

            <section className="relative z-10 flex min-h-dvh items-center justify-center px-4 pt-24 pb-28 md:justify-end md:px-[clamp(24px,7vw,112px)] md:pt-28 md:pb-24">
                <div className="w-full max-w-[460px]">
                    <Card className="w-full shadow-2xl" styles={{ body: { padding: 28 } }}>
                        {children}
                    </Card>
                </div>
            </section>

            <div className="fixed bottom-20 left-1/2 z-20 hidden -translate-x-1/2 md:block">
                <Button
                    aria-label={videoPlaying ? "暂停背景视频" : "继续播放背景视频"}
                    aria-pressed={!videoPlaying}
                    className="border-(--auth-border) bg-(--auth-surface) text-xs font-medium whitespace-nowrap text-(--auth-foreground-subtle) backdrop-blur-md hover:bg-(--auth-surface-hover) hover:text-(--auth-foreground)"
                    onClick={toggleVideo}
                    type="text"
                >
                    {videoPlaying ? (
                        <Pause className="size-3.5 text-blue-400" />
                    ) : (
                        <Play className="size-3.5 text-blue-400" />
                    )}
                    {videoPlaying ? "创意正在进行" : "开始发挥创意"}
                </Button>
            </div>

            <footer className="fixed inset-x-0 bottom-0 z-20 flex flex-wrap items-center justify-center gap-x-4 gap-y-2 bg-[image:var(--auth-footer-gradient)] px-4 py-4 text-[11px] text-(--auth-foreground-subtle)">
                <span className="w-full text-center whitespace-nowrap sm:w-auto">
                    © {new Date().getFullYear()} {BRAND_NAME}。保留所有权利。
                </span>
                <Link
                    className="whitespace-nowrap transition-colors hover:text-(--auth-foreground)"
                    href="/sign-in?routes=legal&tab=terms"
                >
                    用户协议
                </Link>
                <Link
                    className="whitespace-nowrap transition-colors hover:text-(--auth-foreground)"
                    href="/sign-in?routes=legal&tab=privacy"
                >
                    隐私政策
                </Link>
                <Link
                    className="whitespace-nowrap transition-colors hover:text-(--auth-foreground)"
                    href="/sign-in?routes=legal&tab=confidentiality"
                >
                    数据保密协议
                </Link>
            </footer>
        </main>
    );
}
