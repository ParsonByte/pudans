"use client";

import { useRouter } from "next/navigation";

import {
    ChevronLeftIcon,
    EllipsisIcon,
    FullscreenIcon,
    HeartIcon,
    ImageIcon,
    PauseIcon,
    PlayCircleIcon,
    SoundIcon,
} from "tdesign-icons-react";

import { INDEX_PAGE_KEYS, indexPageHref } from "@/routes/(_workspace)/(_index)/paths";

import { ACTIVE_NEXT_VIDEO_INDEX, NEXT_VIDEO_KEYS, useExplorePlayerBodyLock } from "./composables/_explore-player";
import styles from "./styles/explore-player-view.module.css";

type ExplorePlayerViewProps = {
    name: string;
};

export function ExplorePlayerView({ name }: ExplorePlayerViewProps) {
    const router = useRouter();

    useExplorePlayerBodyLock();

    return (
        <section aria-labelledby="explore-player-title" className={styles.page} data-explore-player-id={name}>
            <h1 className="sr-only" id="explore-player-title">
                探索作品播放页
            </h1>

            <div className={styles.filmstrip}>
                <button
                    aria-label="返回探索页"
                    className={styles.backButton}
                    onClick={() => {
                        if (window.history.length > 1) {
                            router.back();
                            return;
                        }

                        router.push(indexPageHref(INDEX_PAGE_KEYS.explore));
                    }}
                    type="button"
                >
                    <ChevronLeftIcon size={20} />
                </button>
                <div aria-label="下一个视频" className={styles.thumbRail} role="group">
                    {NEXT_VIDEO_KEYS.map((key, index) => {
                        const active = index === ACTIVE_NEXT_VIDEO_INDEX;

                        return (
                            <button
                                aria-current={active ? "true" : undefined}
                                aria-disabled="true"
                                aria-label="下一个视频，接口待接入"
                                className={`${styles.thumb} ${active ? styles.thumbActive : ""}`}
                                key={key}
                                title="下一个视频接口待接入"
                                type="button"
                            >
                                <ImageIcon size={17} />
                                <span aria-hidden className={styles.thumbCopy}>
                                    <span className={styles.thumbTitle} />
                                    <span className={styles.thumbMeta} />
                                </span>
                            </button>
                        );
                    })}
                </div>
            </div>

            <div className={styles.stageArea}>
                <div aria-label="探索作品播放区域，播放接口待接入" className={styles.player} role="region">
                    <span className={styles.pendingBadge}>探索作品接口待接入</span>
                    <span className={styles.playbackState}>
                        <span className={styles.playbackIcon}>
                            <PlayCircleIcon size={36} />
                        </span>
                        <span className={styles.playbackLabel}>播放接口待接入</span>
                    </span>

                    <div className={styles.overlay}>
                        <div className={styles.metaRow}>
                            <div className={styles.metaCopy}>
                                <span className={styles.title}>作品标题接口待接入</span>
                                <div className={styles.authorRow}>
                                    <span aria-hidden className={styles.avatar} />
                                    <span className={styles.authorName}>作者接口待接入</span>
                                    <button
                                        aria-disabled="true"
                                        aria-label="关注作者，接口待接入"
                                        className={styles.followButton}
                                        title="关注接口待接入"
                                        type="button"
                                    >
                                        关注
                                    </button>
                                </div>
                            </div>
                            <div className={styles.metaActions}>
                                <button
                                    aria-disabled="true"
                                    aria-label="点赞，接口待接入"
                                    className={styles.actionButton}
                                    title="点赞接口待接入"
                                    type="button"
                                >
                                    <HeartIcon size={19} />
                                </button>
                                <button
                                    aria-disabled="true"
                                    aria-label="更多操作，接口待接入"
                                    className={styles.actionButton}
                                    title="更多操作接口待接入"
                                    type="button"
                                >
                                    <EllipsisIcon size={21} />
                                </button>
                            </div>
                        </div>

                        <div className={styles.controls}>
                            <button
                                aria-disabled="true"
                                aria-label="播放或暂停，接口待接入"
                                className={styles.controlButton}
                                title="播放接口待接入"
                                type="button"
                            >
                                <PauseIcon size={18} />
                            </button>
                            <span className={styles.time}>00:00 / --:--</span>
                            <span aria-hidden className={styles.progressTrack}>
                                <span className={styles.progressThumb} />
                            </span>
                            <button
                                aria-disabled="true"
                                aria-label="音量，接口待接入"
                                className={styles.controlButton}
                                title="音量接口待接入"
                                type="button"
                            >
                                <SoundIcon size={18} />
                            </button>
                            <button
                                aria-disabled="true"
                                aria-label="清晰度，接口待接入"
                                className={styles.qualityButton}
                                title="清晰度接口待接入"
                                type="button"
                            >
                                画质
                            </button>
                            <button
                                aria-disabled="true"
                                aria-label="全屏，接口待接入"
                                className={styles.controlButton}
                                title="全屏接口待接入"
                                type="button"
                            >
                                <FullscreenIcon size={18} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
