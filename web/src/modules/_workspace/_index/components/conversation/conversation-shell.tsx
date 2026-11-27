"use client";

import { useState } from "react";

import type { ActivityContent } from "@tdesign-react/chat";
import { AddIcon, ChatIcon } from "tdesign-icons-react";
import { Button, MessagePlugin } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { useConsoleSession } from "@/modules/_workspace/_console/composables/_session";

import type { ConversationController } from "../../composables/_conversation-controller";
import { ConversationActivityPanel } from "./conversation-activity-panel";
import { ConversationComposer } from "./conversation-composer";
import { ConversationSessionTabs } from "./conversation-session-tabs";
import { ConversationThread } from "./conversation-thread";
import styles from "./conversation.module.css";

type ConversationShellProps = {
    controller: ConversationController;
};

export function ConversationShell({ controller }: ConversationShellProps) {
    const sessionQuery = useConsoleSession();
    const viewerName = sessionQuery.data?.displayName || sessionQuery.data?.username || "当前用户";
    const [selectedActivity, setSelectedActivity] = useState<{
        content: ActivityContent;
        sessionId: string | null;
    } | null>(null);
    const [dismissedActivity, setDismissedActivity] = useState<string | null>(null);
    const latestActivity = controller.latestActivity;
    const activityKey = latestActivity ? activityIdentity(latestActivity) : null;
    const manuallySelectedActivity =
        selectedActivity?.sessionId === controller.activeSessionId ? selectedActivity.content : null;
    const activity =
        manuallySelectedActivity ?? (latestActivity && activityKey !== dismissedActivity ? latestActivity : null);
    const messagesError =
        controller.messages.length === 0 ? (controller.messagesError ?? controller.activeSessionError) : undefined;
    const activeTitle =
        controller.activeSession?.title?.trim() || (controller.activeSessionId ? "未命名会话" : "新对话");

    const createSession = async () => {
        try {
            await controller.createSession();
        } catch (error) {
            MessagePlugin.error(consoleErrorMessage(error, error instanceof Error ? error.message : "新建会话失败。"));
        }
    };

    const sendPrompt = (prompt: string) => {
        void controller.send({ files: [], prompt }).catch((error: unknown) => {
            MessagePlugin.error(consoleErrorMessage(error, error instanceof Error ? error.message : "消息发送失败。"));
        });
    };

    const decideApproval = async (approvalId: string, toolCallId: string, decision: "approve" | "reject") => {
        try {
            await controller.decideApproval(approvalId, toolCallId, decision);
        } catch (error) {
            MessagePlugin.error(consoleErrorMessage(error, error instanceof Error ? error.message : "工具审批失败。"));
        }
    };

    return (
        <section aria-labelledby="conversation-title" className={styles.page}>
            <header className={styles.header}>
                <div className={styles.headerIdentity}>
                    <span className={styles.headerIcon}>
                        <ChatIcon size={18} />
                    </span>
                    <div className={styles.headerText}>
                        <h1 className={styles.title} id="conversation-title">
                            {activeTitle}
                        </h1>
                        <div className={styles.subtitle}>
                            {controller.activeSessionId ? "当前工作区会话" : "发送第一条消息后自动保存会话"}
                        </div>
                    </div>
                </div>
                <div className={styles.headerActions}>
                    <Button
                        icon={<AddIcon />}
                        loading={controller.creatingSession}
                        onClick={() => void createSession()}
                        theme="primary"
                        variant="outline"
                    >
                        新建会话
                    </Button>
                </div>
            </header>

            <ConversationSessionTabs
                activeSessionId={controller.activeSessionId}
                creating={controller.creatingSession}
                onClose={controller.closeSession}
                onCreate={() => void createSession()}
                onSelect={controller.selectSession}
                sessions={controller.tabs}
            />

            <div className={styles.body}>
                <div className={styles.main}>
                    <ConversationThread
                        activeSessionId={controller.activeSessionId}
                        errorMessage={messagesError ? consoleErrorMessage(messagesError) : undefined}
                        hasOlder={controller.hasOlder}
                        isFetchingOlder={controller.isFetchingOlder}
                        isLoading={controller.isLoadingMessages}
                        messages={controller.messages}
                        onApproval={decideApproval}
                        onLoadOlder={controller.loadOlder}
                        onOpenActivity={(content) => {
                            setDismissedActivity(null);
                            setSelectedActivity({
                                content,
                                sessionId: controller.activeSessionId,
                            });
                        }}
                        onReplay={(prompt) => {
                            void controller.send({ files: [], prompt }).catch((error: unknown) => {
                                MessagePlugin.error(
                                    consoleErrorMessage(
                                        error,
                                        error instanceof Error ? error.message : "重新生成失败。",
                                    ),
                                );
                            });
                        }}
                        onRetry={controller.reloadMessages}
                        onSendPrompt={sendPrompt}
                        viewer={{
                            avatar: sessionQuery.data?.avatar,
                            name: viewerName,
                        }}
                    />
                    <ConversationComposer
                        onSend={controller.send}
                        onStop={controller.stop}
                        running={controller.running}
                        sending={Boolean(controller.activePhase)}
                        sendingLabel={controller.submissionLabel}
                    />
                </div>
                {activity ? (
                    <ConversationActivityPanel
                        activity={activity}
                        onClose={() => {
                            setDismissedActivity(activityKey);
                            setSelectedActivity(null);
                        }}
                    />
                ) : null}
            </div>
        </section>
    );
}

function activityIdentity(activity: ActivityContent): string {
    return `${activity.data.activityType}:${activity.data.messageId ?? "current"}`;
}
