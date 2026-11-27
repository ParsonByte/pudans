"use client";

import Link from "next/link";

import { useQuery } from "@tanstack/react-query";
import { AiImageIcon, BrowseGalleryIcon, CalendarIcon, FileIcon } from "tdesign-icons-react";
import { Tag } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatCount } from "@/modules/_workspace/_console/composables/_format";
import {
    type Activity,
    type Template,
    type Work,
    listActivities,
    listExploreWorks,
    listTemplates,
} from "@/modules/_workspace/_console/modules/dans/composables/_dans";
import { indexActivityHref, indexWorkHref } from "@/routes/(_workspace)/(_index)/paths";

import { CREATE_SECTIONS, type CreateSection } from "../composables/_create";
import { useCreationTasks } from "../composables/_generation";
import styles from "../styles/create-view.module.css";
import { CreateGenerationStatus, CreateGenerationTiles } from "./create-generation-results";

const STATUS_LABELS: Record<string, string> = {
    approved: "审核通过",
    draft: "草稿",
    published: "已上架",
    rejected: "审核拒绝",
};

function statusLabel(status?: string): string {
    return status ? (STATUS_LABELS[status] ?? status) : "未知";
}

function FeedNotice({ error }: { error: unknown }) {
    return <div className={styles.feedNotice}>{consoleErrorMessage(error)}</div>;
}

function DiscoverFeed() {
    const tasksQuery = useCreationTasks();
    const worksQuery = useQuery({
        queryFn: () => listExploreWorks(100),
        queryKey: ["index", "creation", "works"],
    });
    const tasks = tasksQuery.data ?? [];
    const works = worksQuery.data?.items ?? [];
    const error = tasksQuery.error ?? worksQuery.error;

    return (
        <>
            {error ? <FeedNotice error={error} /> : null}
            <CreateGenerationStatus tasks={tasks} />
            <div className={styles.discoverMasonry}>
                <CreateGenerationTiles tasks={tasks} />
                {works.map((work) => (
                    <WorkTile key={work.id} work={work} />
                ))}
            </div>
        </>
    );
}

function WorkTile({ work }: { work: Work }) {
    const title = work.title ?? "未命名作品";

    return (
        <article className={`${styles.feedTile} ${styles.workTile}`}>
            <Link aria-label={`打开作品 ${title}`} className={styles.feedTileLink} href={indexWorkHref(work.id)} />
            <div aria-hidden className={styles.feedTileVisual}>
                <span className={styles.feedTileGlyph}>
                    <BrowseGalleryIcon size={24} />
                </span>
                <span className={styles.feedTileLabel}>{title}</span>
            </div>
            <div className={styles.feedTileMeta}>
                <span className={styles.feedTileByline}>
                    <Tag size="small" theme={work.status === "published" ? "success" : "default"} variant="light">
                        {statusLabel(work.status)}
                    </Tag>
                    <span>{formatCount(work.viewCount)} 次浏览</span>
                </span>
                <span className={styles.feedTileByline}>{formatCount(work.likeCount)} 次点赞</span>
            </div>
        </article>
    );
}

function TemplateFeed() {
    const query = useQuery({
        queryFn: () => listTemplates(100),
        queryKey: ["index", "creation", "templates"],
    });
    const templates = query.data?.items ?? [];

    return (
        <>
            {query.isError ? <FeedNotice error={query.error} /> : null}
            <div className={`${styles.masonry} ${styles.templateMasonry}`}>
                {templates.map((template) => (
                    <TemplateTile key={template.id} template={template} />
                ))}
            </div>
        </>
    );
}

function TemplateTile({ template }: { template: Template }) {
    const title = template.title ?? template.name ?? "未命名模板";

    return (
        <article className={styles.feedTile}>
            <div aria-hidden className={styles.feedTileVisual}>
                <span className={styles.feedTileGlyph}>
                    <TemplateGlyph mediaType={template.mediaType} />
                </span>
                <span className={styles.feedTileLabel}>{title}</span>
            </div>
            <div className={styles.feedTileMeta}>
                <span className={styles.feedTileDescription}>{template.description ?? "暂无描述"}</span>
                <span className={styles.feedTileByline}>
                    <Tag size="small" theme={template.status === "published" ? "success" : "default"} variant="light">
                        {statusLabel(template.status)}
                    </Tag>
                    <span>{template.mediaType ?? "未知类型"}</span>
                </span>
            </div>
        </article>
    );
}

function TemplateGlyph({ mediaType }: { mediaType?: string }) {
    return mediaType === "image" || mediaType === "video" ? <AiImageIcon size={24} /> : <FileIcon size={24} />;
}

function ActivityFeed() {
    const query = useQuery({
        queryFn: () => listActivities(100),
        queryKey: ["index", "creation", "activities"],
    });
    const activities = query.data?.items ?? [];

    return (
        <>
            {query.isError ? <FeedNotice error={query.error} /> : null}
            <div className={styles.activityGrid}>
                {activities.map((activity) => (
                    <ActivityTile activity={activity} key={activity.id} />
                ))}
            </div>
        </>
    );
}

function ActivityTile({ activity }: { activity: Activity }) {
    const title = activity.title ?? "未命名活动";

    return (
        <article className={styles.activityCard}>
            <Link
                aria-label={`打开创作活动 ${title}`}
                className={styles.activityCardLink}
                href={indexActivityHref(activity.id)}
            />
            <span aria-hidden className={styles.activityCover}>
                <CalendarIcon size={28} />
            </span>
            <span className={styles.activityBody}>
                <span className={styles.activityTitle}>{title}</span>
                <span className={styles.activityDescription}>{activity.description ?? "暂无描述"}</span>
                <span className={styles.activityMeta}>
                    <Tag size="small" theme={activity.status === "published" ? "success" : "default"} variant="light">
                        {statusLabel(activity.status)}
                    </Tag>
                    <span>
                        {formatDate(activity.startsAt)} - {formatDate(activity.endsAt)}
                    </span>
                </span>
            </span>
        </article>
    );
}

function formatDate(value?: string): string {
    if (!value) {
        return "不限";
    }

    return new Intl.DateTimeFormat("zh-CN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
    }).format(new Date(value));
}

type CreateFeedProps = {
    section: CreateSection;
};

export function CreateFeed({ section }: CreateFeedProps) {
    if (section === CREATE_SECTIONS.templates) {
        return <TemplateFeed />;
    }
    if (section === CREATE_SECTIONS.activities) {
        return <ActivityFeed />;
    }
    return <DiscoverFeed />;
}
