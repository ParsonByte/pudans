"use client";

import dynamic from "next/dynamic";

import type { ActivityContent } from "@tdesign-react/chat";
import { CloseIcon } from "tdesign-icons-react";
import { Button } from "tdesign-react";

import styles from "./conversation.module.css";

const ConversationJsonRender = dynamic(
    () => import("./conversation-json-render").then((module) => module.ConversationJsonRender),
    { ssr: false },
);

const ActivityRenderer = dynamic(() => import("@tdesign-react/chat").then((module) => module.ActivityRenderer), {
    ssr: false,
});

type ConversationActivityPanelProps = {
    activity: ActivityContent;
    onClose: () => void;
};

export function ConversationActivityPanel({ activity, onClose }: ConversationActivityPanelProps) {
    const content = objectValue(activity.data.content);
    const jsonRender = isJsonRenderSchema(content);

    return (
        <aside aria-label="生成式界面" className={styles.activityPanel}>
            <header className={styles.activityHeader}>
                <div className={styles.activityHeading}>
                    <h2 className={styles.activityTitle}>生成式界面</h2>
                    <div className={styles.activityType}>{activity.data.activityType}</div>
                </div>
                <Button
                    aria-label="关闭生成式界面"
                    icon={<CloseIcon />}
                    onClick={onClose}
                    shape="square"
                    variant="text"
                />
            </header>
            <div className={styles.activityBody}>
                {jsonRender ? (
                    <ConversationJsonRender
                        activityType={activity.data.activityType}
                        content={content}
                        messageId={activity.data.messageId}
                    />
                ) : (
                    <div className={styles.activityFallback}>
                        <p className={styles.activityFallbackText}>
                            当前活动类型没有可用的展示组件，以下为服务端返回的真实结构。
                        </p>
                        <ActivityRenderer activity={activity.data} />
                        <details>
                            <summary className={styles.activityFallbackText}>查看原始数据</summary>
                            <pre className={styles.activityJson}>{JSON.stringify(content, null, 2)}</pre>
                        </details>
                    </div>
                )}
            </div>
        </aside>
    );
}

function isJsonRenderSchema(value: Record<string, unknown>): boolean {
    return (
        (typeof value.root === "string" || (value.root !== null && typeof value.root === "object")) &&
        value.elements !== null &&
        typeof value.elements === "object"
    );
}

function objectValue(value: unknown): Record<string, unknown> {
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}
