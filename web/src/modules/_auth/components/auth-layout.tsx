"use client";

import { type ReactNode, useEffect, useRef, useState } from "react";

import Image from "next/image";
import Link from "next/link";

import { PauseIcon, PlayIcon } from "tdesign-icons-react";
import { Button, Card } from "tdesign-react";

import { ThemeMenu } from "@/components/ui";
import { useMobile } from "@/hooks/ui/use-mobile";
import { useEventCallback } from "@/hooks/utils/use-event-callback";

import { AUTH_GLASS_CLASS } from "../styles/glass";

const BRAND_NAME = "PUDANS";

type AuthLayoutVariant = "auth" | "setup";

type AuthLayoutProps = {
    children: ReactNode;
    variant?: AuthLayoutVariant;
};

export function AuthLayout({ children, variant = "auth" }: AuthLayoutProps) {
    const videoRef = useRef<HTMLVideoElement>(null);
    const isMobile = useMobile();
    const [videoPlaying, setVideoPlaying] = useState(false);
    const isSetup = variant === "setup";

    useEffect(() => {
        window.scrollTo(0, 0);

        const video = videoRef.current;

        if (video) {
            void video.play().catch(() => undefined);
            setVideoPlaying(!video.paused);
        }
    }, []);

    const toggleVideo = useEventCallback(() => {
        const video = videoRef.current;

        if (!video) {
            return;
        }

        if (video.paused) {
            void video.play();
        } else {
            video.pause();
        }
    });

    return (
        <main className="relative isolate h-dvh overflow-hidden bg-[var(--td-gray-color-14)] text-(--auth-foreground)">
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
                onPause={() => setVideoPlaying(false)}
                onPlay={() => setVideoPlaying(true)}
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
                <Link
                    href={isSetup ? "/setup" : "/sign-in"}
                    aria-label={BRAND_NAME}
                    className="inline-flex min-w-0 items-center gap-2.5"
                    style={{ color: "var(--auth-foreground)" }}
                >
                    <Image alt="" className="shrink-0" height={32} src="/brand/icon-small.svg" unoptimized width={32} />
                    <span className="truncate text-sm leading-none font-bold tracking-[0.16em]">{BRAND_NAME}</span>
                </Link>
                <ThemeMenu
                    buttonClassName="drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)] transition-opacity hover:opacity-80"
                    buttonStyle={{ color: "var(--auth-foreground)" }}
                />
            </header>

            {isSetup ? null : (
                <section className="pointer-events-none fixed bottom-[clamp(88px,12vh,132px)] left-[clamp(28px,6vw,104px)] z-10 hidden max-w-[min(620px,48vw)] text-(--auth-foreground) [filter:drop-shadow(var(--auth-drop-shadow))] md:block">
                    <p className="mb-4 text-xs font-semibold tracking-[0.22em] text-(--auth-foreground-muted)">
                        PUDANS · 数字空间
                    </p>
                    <h1 className="flex flex-col gap-0.5 text-[clamp(32px,4.1vw,58px)] leading-[1.16] font-semibold tracking-[-0.045em]">
                        <span>让每一个灵感，</span>
                        <span>成为看得见的作品。</span>
                    </h1>
                </section>
            )}

            <section
                className={`relative z-10 flex h-dvh justify-center px-4 md:px-[clamp(24px,7vw,112px)] ${
                    isSetup ? "pt-20 pb-10 md:pt-24 md:pb-12" : "pt-20 pb-20 md:justify-end md:pt-28 md:pb-24"
                }`}
            >
                <div
                    className={`flex h-full w-full flex-col justify-center ${
                        isSetup ? "max-w-[min(720px,calc(100vw-2rem))]" : "max-w-[460px]"
                    }`}
                >
                    <Card
                        className={`${AUTH_GLASS_CLASS} max-h-full w-full overflow-y-auto`}
                        bodyStyle={{ padding: isMobile ? 20 : isSetup ? 32 : 28 }}
                    >
                        {children}
                    </Card>
                </div>
            </section>

            {isSetup ? null : (
                <div className="fixed bottom-20 left-1/2 z-20 hidden -translate-x-1/2 md:block">
                    <Button
                        aria-label={videoPlaying ? "暂停背景视频" : "继续播放背景视频"}
                        aria-pressed={!videoPlaying}
                        className="text-xs font-medium whitespace-nowrap drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)] backdrop-blur-md transition-opacity hover:opacity-80"
                        onClick={toggleVideo}
                        shape="round"
                        style={{
                            backgroundColor: "var(--auth-surface)",
                            borderColor: "var(--auth-border)",
                            color: "var(--auth-foreground)",
                        }}
                        variant="text"
                    >
                        <span className="inline-flex items-center gap-1.5">
                            {videoPlaying ? (
                                <PauseIcon className="size-3.5 shrink-0 text-(--auth-accent)" />
                            ) : (
                                <PlayIcon className="size-3.5 shrink-0 text-(--auth-accent)" />
                            )}
                            <span>{videoPlaying ? "创意正在进行" : "开始发挥创意"}</span>
                        </span>
                    </Button>
                </div>
            )}

            {isSetup ? null : (
                <footer className="fixed inset-x-0 bottom-0 z-20 flex w-full flex-wrap items-center justify-center gap-x-4 gap-y-2 bg-[image:var(--auth-footer-gradient)] px-4 py-4 text-[11px] text-(--auth-foreground-subtle)">
                    <span className="w-full text-center whitespace-nowrap sm:w-auto">
                        © {new Date().getFullYear()} {BRAND_NAME}。保留所有权利。
                    </span>
                    <Link
                        className="whitespace-nowrap"
                        href="/sign-in?routes=legal&tab=terms"
                        style={{ color: "var(--auth-foreground-subtle)" }}
                    >
                        用户协议
                    </Link>
                    <Link
                        className="whitespace-nowrap"
                        href="/sign-in?routes=legal&tab=privacy"
                        style={{ color: "var(--auth-foreground-subtle)" }}
                    >
                        隐私政策
                    </Link>
                    <Link
                        className="whitespace-nowrap"
                        href="/sign-in?routes=legal&tab=confidentiality"
                        style={{ color: "var(--auth-foreground-subtle)" }}
                    >
                        数据保密协议
                    </Link>
                </footer>
            )}
        </main>
    );
}
