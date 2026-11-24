"use client";

import Link from "next/link";

import { SparklesIcon } from "lucide-react";
import {
    AiImageIcon,
    AudioIcon,
    ChatHeartIcon,
    ChevronDownIcon,
    ChevronLeftIcon,
    DownloadIcon,
    EditIcon,
    ExpandHorizontalIcon,
    FlagIcon,
    HdIcon,
    HeartIcon,
    ImageIcon,
    PlayCircleIcon,
    RefreshIcon,
    ShareIcon,
    StarIcon,
    ViewModuleIcon,
} from "tdesign-icons-react";

import { INDEX_PAGE_KEYS, indexPageHref } from "@/routes/(_workspace)/(_index)/paths";

import { RELATED_PLACEHOLDER_KEYS } from "./composables/_work-detail";
import styles from "./styles/work-detail-view.module.css";

const REACTION_ACTIONS = [
    { icon: <HeartIcon size={17} />, label: "点赞" },
    { icon: <DownloadIcon size={17} />, label: "下载" },
    { icon: <StarIcon size={17} />, label: "收藏" },
    { icon: <ShareIcon size={17} />, label: "分享" },
    { icon: <FlagIcon size={17} />, label: "举报" },
];

const EDIT_ACTIONS = [
    { icon: <HdIcon size={17} />, label: "AI 画质增强" },
    { icon: <RefreshIcon size={17} />, label: "续写" },
    { icon: <ExpandHorizontalIcon size={17} />, label: "视频扩幅" },
    { icon: <EditIcon size={17} />, label: "编辑" },
    { icon: <ChatHeartIcon size={17} />, label: "口型同步" },
    { icon: <AudioIcon size={17} />, label: "音效" },
    { icon: <ViewModuleIcon size={17} />, label: "视频深度图" },
];

type WorkDetailViewProps = {
    name: string;
};

export function WorkDetailView({ name }: WorkDetailViewProps) {
    return (
        <section aria-labelledby="work-detail-title" className={styles.page} data-work-id={name}>
            <h1 className="sr-only" id="work-detail-title">
                作品播放页
            </h1>
            <div className={styles.scroller}>
                <div className={styles.topBar}>
                    <Link
                        aria-label="返回创作页"
                        className={styles.backButton}
                        href={indexPageHref(INDEX_PAGE_KEYS.create)}
                    >
                        <ChevronLeftIcon size={22} />
                    </Link>
                    <span className={styles.pendingBadge}>作品详情接口待接入</span>
                </div>

                <div className={styles.layout}>
                    <div className={styles.primaryColumn}>
                        <div aria-label="作品播放区域，接口待接入" className={styles.mediaStage} role="img">
                            <span className={styles.mediaIcon}>
                                <PlayCircleIcon size={34} />
                            </span>
                            <span className={styles.mediaLabel}>播放接口待接入</span>
                        </div>
                        <div aria-label="作品互动" className={styles.reactionBar} role="group">
                            {REACTION_ACTIONS.map((action) => (
                                <button
                                    aria-disabled="true"
                                    className={styles.reactionButton}
                                    key={action.label}
                                    title="作品详情接口待接入"
                                    type="button"
                                >
                                    {action.icon}
                                    {action.label}
                                </button>
                            ))}
                            <button
                                aria-disabled="true"
                                className={styles.watermarkButton}
                                title="作品详情接口待接入"
                                type="button"
                            >
                                移除 AI 生成水印
                                <span aria-hidden className={styles.switchTrack}>
                                    <span className={styles.switchThumb} />
                                </span>
                            </button>
                        </div>
                    </div>

                    <aside aria-label="作品信息" className={styles.infoColumn}>
                        <div className={styles.creatorRow}>
                            <span aria-hidden className={styles.avatar} />
                            <span className={styles.creatorCopy}>
                                <span className={styles.skeletonLineMedium} />
                                <span className={styles.skeletonLineShort} />
                            </span>
                            <span className={styles.aiBadge}>
                                <AiImageIcon size={14} />
                                AI 生成
                            </span>
                        </div>

                        <section className={styles.promptCard}>
                            <div className={styles.promptHeader}>
                                <strong>提示词</strong>
                                <button
                                    aria-disabled="true"
                                    className={styles.copyButton}
                                    title="作品详情接口待接入"
                                    type="button"
                                >
                                    待接入
                                </button>
                            </div>
                            <div aria-hidden className={styles.promptCopy}>
                                <span className={styles.skeletonLineWide} />
                                <span className={styles.skeletonLineFull} />
                                <span className={styles.skeletonLineMedium} />
                            </div>
                            <span aria-hidden className={styles.referenceThumb}>
                                <ImageIcon size={20} />
                            </span>
                            <button
                                aria-disabled="true"
                                className={styles.detailsToggle}
                                title="作品详情接口待接入"
                                type="button"
                            >
                                详情
                                <ChevronDownIcon size={16} />
                            </button>
                        </section>

                        <div className={styles.sectionHeader}>编辑</div>
                        <div className={styles.editGrid}>
                            {EDIT_ACTIONS.map((action) => (
                                <button
                                    aria-disabled="true"
                                    className={styles.editButton}
                                    key={action.label}
                                    title="作品详情接口待接入"
                                    type="button"
                                >
                                    {action.icon}
                                    {action.label}
                                </button>
                            ))}
                        </div>

                        <div className={styles.createBlock}>
                            <div className={styles.sectionHeader}>创建</div>
                            <div className={styles.createActions}>
                                <button
                                    aria-disabled="true"
                                    className={styles.retryButton}
                                    title="作品详情接口待接入"
                                    type="button"
                                >
                                    <RefreshIcon size={17} />
                                    重试
                                </button>
                                <Link className={styles.primaryAction} href={indexPageHref(INDEX_PAGE_KEYS.create)}>
                                    <SparklesIcon size={17} />
                                    做同款
                                </Link>
                            </div>
                        </div>
                    </aside>

                    <aside aria-label="关联作品" className={styles.relatedRail}>
                        <div aria-hidden className={styles.relatedList}>
                            {RELATED_PLACEHOLDER_KEYS.map((key) => (
                                <span className={styles.relatedThumb} key={key}>
                                    <ImageIcon size={18} />
                                </span>
                            ))}
                        </div>
                    </aside>
                </div>
            </div>
        </section>
    );
}
