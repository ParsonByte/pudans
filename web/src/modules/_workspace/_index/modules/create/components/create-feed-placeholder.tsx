import type { ReactNode } from "react";

import Link from "next/link";

import { SparklesIcon } from "lucide-react";
import { AiImageIcon, BrowseGalleryIcon, CalendarIcon, DownloadIcon, ShareIcon } from "tdesign-icons-react";

import { indexActivityHref, indexWorkHref } from "@/routes/(_workspace)/(_index)/paths";

import styles from "../styles/create-view.module.css";

const DISCOVER_TILE_KEYS = Array.from({ length: 12 }, (_, index) => `discover-placeholder-${index}`);
const TEMPLATE_TILE_KEYS = Array.from({ length: 15 }, (_, index) => `template-placeholder-${index}`);
const ACTIVITY_CARD_KEYS = Array.from({ length: 6 }, (_, index) => `activity-placeholder-${index}`);

type CreateFeedSection = "activities" | "discover" | "templates";

type CreateFeedPlaceholderProps = {
    section: CreateFeedSection;
};

type PendingFeedContent = {
    description: string;
    icon: ReactNode;
    title: string;
};

const PENDING_FEED_CONTENT: Record<CreateFeedSection, PendingFeedContent> = {
    activities: {
        description: "接入后展示投稿活动、截止时间与评选信息。",
        icon: <CalendarIcon size={18} />,
        title: "创作活动接口待接入",
    },
    discover: {
        description: "接入后展示精选作品、活动横幅与最新创作。",
        icon: <BrowseGalleryIcon size={18} />,
        title: "作品流接口待接入",
    },
    templates: {
        description: "接入后可按风格筛选模板并一键复用。",
        icon: <AiImageIcon size={18} />,
        title: "模板库接口待接入",
    },
};

function PendingNotice({ section }: CreateFeedPlaceholderProps) {
    const content = PENDING_FEED_CONTENT[section];

    return (
        <div className={styles.pendingNotice} role="status">
            <span className={styles.pendingIcon}>{content.icon}</span>
            <span className={styles.pendingCopy}>
                <strong>{content.title}</strong>
                <span>{content.description}</span>
            </span>
            <span className={styles.pendingTag}>版式占位</span>
        </div>
    );
}

function FeedTileActions({ pendingLabel }: { pendingLabel: string }) {
    return (
        <div className={styles.feedTileActions}>
            <button
                aria-disabled="true"
                className={`${styles.feedTileAction} ${styles.feedTileActionPrimary}`}
                title={pendingLabel}
                type="button"
            >
                <SparklesIcon aria-hidden size={18} />
                做同款
            </button>
            <button
                aria-disabled="true"
                aria-label="下载"
                className={`${styles.feedTileAction} ${styles.feedTileActionIcon}`}
                title={pendingLabel}
                type="button"
            >
                <DownloadIcon aria-hidden size={18} />
            </button>
            <button
                aria-disabled="true"
                aria-label="分享"
                className={`${styles.feedTileAction} ${styles.feedTileActionIcon}`}
                title={pendingLabel}
                type="button"
            >
                <ShareIcon aria-hidden size={18} />
            </button>
        </div>
    );
}

function FeedTile({
    actionsPendingLabel,
    icon,
    label,
}: {
    actionsPendingLabel?: string;
    icon: ReactNode;
    label: string;
}) {
    return (
        <div className={styles.feedTile}>
            <Link aria-label="打开作品播放页" className={styles.feedTileLink} href={indexWorkHref("preview")} />
            <div aria-hidden className={styles.feedTileVisual}>
                <span className={styles.feedTileGlyph}>{icon}</span>
                <span className={styles.feedTileLabel}>{label}</span>
            </div>
            <div aria-hidden className={styles.feedTileMeta}>
                <span className={`${styles.skeletonBar} ${styles.skeletonBarWide}`} />
                <span className={styles.feedTileByline}>
                    <span className={styles.feedTileAvatar} />
                    <span className={`${styles.skeletonBar} ${styles.skeletonBarShort}`} />
                </span>
            </div>
            {actionsPendingLabel ? <FeedTileActions pendingLabel={actionsPendingLabel} /> : null}
        </div>
    );
}

function DiscoverPlaceholder() {
    return (
        <>
            <PendingNotice section="discover" />
            <div className={styles.discoverMasonry}>
                {DISCOVER_TILE_KEYS.map((key) => (
                    <FeedTile
                        actionsPendingLabel="作品流接口待接入"
                        icon={<BrowseGalleryIcon size={24} />}
                        key={key}
                        label="作品封面待接入"
                    />
                ))}
            </div>
        </>
    );
}

function TemplatesPlaceholder() {
    return (
        <>
            <PendingNotice section="templates" />
            <div className={`${styles.masonry} ${styles.templateMasonry}`}>
                {TEMPLATE_TILE_KEYS.map((key) => (
                    <FeedTile
                        actionsPendingLabel="模板库接口待接入"
                        icon={<AiImageIcon size={24} />}
                        key={key}
                        label="模板预览待接入"
                    />
                ))}
            </div>
        </>
    );
}

function ActivitiesPlaceholder() {
    return (
        <>
            <PendingNotice section="activities" />
            <div className={styles.activityGrid}>
                {ACTIVITY_CARD_KEYS.map((key) => (
                    <div className={styles.activityCard} key={key}>
                        <Link
                            aria-label="打开创作活动页面"
                            className={styles.activityCardLink}
                            href={indexActivityHref("preview")}
                        />
                        <span className={styles.activityCover}>
                            <CalendarIcon size={28} />
                        </span>
                        <span className={styles.activityBody}>
                            <span className={`${styles.skeletonBar} ${styles.skeletonBarWide}`} />
                            <span className={`${styles.skeletonBar} ${styles.skeletonBarMedium}`} />
                            <span className={`${styles.skeletonBar} ${styles.skeletonBarShort}`} />
                        </span>
                    </div>
                ))}
            </div>
        </>
    );
}

export function CreateFeedPlaceholder({ section }: CreateFeedPlaceholderProps) {
    if (section === "templates") {
        return <TemplatesPlaceholder />;
    }

    if (section === "activities") {
        return <ActivitiesPlaceholder />;
    }

    return <DiscoverPlaceholder />;
}
