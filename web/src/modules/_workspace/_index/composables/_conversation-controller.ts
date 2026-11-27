"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import type { AgentMessage, AgentRun } from "@pudans-dev/api-client";
import { type InfiniteData, useInfiniteQuery, useQueries, useQueryClient } from "@tanstack/react-query";
import type { ActivityContent } from "@tdesign-react/chat";

import {
    INDEX_PAGE_KEYS,
    INDEX_RUN_PARAM,
    INDEX_SESSION_PARAM,
    indexPageHref,
} from "@/routes/(_workspace)/(_index)/paths";

import type { ConversationTabItem, ConversationTimelineMessage } from "../components/conversation/conversation-types";
import {
    conversationMessageFromAgent,
    conversationMessageFromLive,
    conversationTitleFromPrompt,
} from "../components/conversation/conversation-utils";
import {
    CONVERSATION_SESSIONS_QUERY_KEY,
    CONVERSATION_SUMMARY_QUERY_KEY,
    type ConversationMessagePage,
    type ConversationSession,
    cancelConversationRun,
    conversationMessagesQueryKey,
    conversationSessionQueryKey,
    createConversationSession,
    decideConversationApproval,
    getConversationRunStatus,
    getConversationSession,
    listConversationMessages,
    renameConversationSession,
    startConversationRun,
    subscribeAgentRunEvents,
    uploadConversationAssets,
} from "./_conversation";
import {
    type ConversationLiveRun,
    applyConversationApprovalDecision,
    applyConversationRunEvent,
    createConversationLiveRun,
} from "./_conversation-run";

const DEFAULT_SESSION_TITLE = "新会话";
const DRAFT_PHASE_KEY = "draft";
const TERMINAL_EVENT_TYPES = new Set(["run.completed", "run.failed", "run.cancelled"]);
const MAX_EVENT_RECONNECTS = 2;
const EVENT_RECONNECT_DELAY_MS = 600;

export type ConversationSubmissionPhase = "creating" | "starting" | "uploading";

export type ConversationSendInput = {
    files: File[];
    prompt: string;
};

export function useConversationController() {
    const router = useRouter();
    const searchParams = useSearchParams();
    const queryClient = useQueryClient();
    const openSessionIds = useMemo(() => unique(searchParams.getAll(INDEX_SESSION_PARAM)), [searchParams]);
    const activeSessionId = openSessionIds[0] ?? null;
    const requestedRunId = searchParams.get(INDEX_RUN_PARAM);
    const sessionQueries = useQueries({
        queries: openSessionIds.map((sessionId) => ({
            queryFn: () => getConversationSession(sessionId),
            queryKey: conversationSessionQueryKey(sessionId),
            staleTime: 30_000,
        })),
    });
    const activeSessionQuery = sessionQueries[0];
    const messagesQuery = useInfiniteQuery<
        ConversationMessagePage,
        Error,
        InfiniteData<ConversationMessagePage, string | undefined>,
        ReturnType<typeof conversationMessagesQueryKey>,
        string | undefined
    >({
        enabled: Boolean(activeSessionId),
        queryFn: ({ pageParam }) =>
            listConversationMessages(activeSessionId ?? "", {
                before: pageParam,
                limit: 50,
            }),
        getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
        initialPageParam: undefined,
        queryKey: conversationMessagesQueryKey(activeSessionId ?? "none"),
    });
    const [creatingSession, setCreatingSession] = useState(false);
    const [liveRuns, setLiveRuns] = useState<Record<string, ConversationLiveRun>>({});
    const [submissionPhases, setSubmissionPhases] = useState<Record<string, ConversationSubmissionPhase>>({});
    const subscriptionsRef = useRef(new Map<string, AbortController>());
    const restoredRunRef = useRef<string | null>(null);
    const watchRunRef = useRef<(run: ConversationLiveRun) => Promise<void>>(async () => undefined);
    const refreshSessionRef = useRef<(sessionId: string) => Promise<unknown>>(async () => undefined);
    const persistedMessages = useMemo(
        () => normalizeMessages(messagesQuery.data?.pages.flatMap((page) => page.items ?? []) ?? []),
        [messagesQuery.data],
    );
    const persistedIds = useMemo(
        () => new Set(persistedMessages.flatMap((message) => (message.id ? [message.id] : []))),
        [persistedMessages],
    );
    const activeLiveRun = activeSessionId ? liveRuns[activeSessionId] : undefined;
    const messages = useMemo(() => {
        const timeline = persistedMessages.map(conversationMessageFromAgent);

        if (!activeLiveRun) {
            return timeline;
        }

        if (activeLiveRun.outputMessageId && persistedIds.has(activeLiveRun.outputMessageId)) {
            return timeline;
        }

        return [...timeline, conversationMessageFromLive(activeLiveRun)];
    }, [activeLiveRun, persistedIds, persistedMessages]);
    const latestActivity = useMemo(() => latestConversationActivity(messages), [messages]);
    const tabs: ConversationTabItem[] = openSessionIds.map((sessionId, index) => {
        const query = sessionQueries[index];
        const title = query?.data?.title?.trim();

        return {
            id: sessionId,
            title: title || (query?.isPending ? "正在加载会话…" : query?.isError ? "会话不可用" : "未命名会话"),
        };
    });
    const running = isActiveRun(activeLiveRun);
    const activePhase = submissionPhases[activeSessionId ?? DRAFT_PHASE_KEY];
    const loadingSession = Boolean(activeSessionId) && activeSessionQuery?.isPending;

    useEffect(
        () => () => {
            for (const controller of subscriptionsRef.current.values()) {
                controller.abort();
            }
            subscriptionsRef.current.clear();
        },
        [],
    );

    useEffect(() => {
        if (!requestedRunId || !activeSessionId || restoredRunRef.current === requestedRunId) {
            return;
        }

        restoredRunRef.current = requestedRunId;
        let cancelled = false;

        void (async () => {
            try {
                const run = await getConversationRunStatus(requestedRunId);

                if (cancelled || run.sessionId !== activeSessionId) {
                    return;
                }

                const liveRun = liveRunFromStatus(createConversationLiveRun(run), run);

                setLiveRuns((current) => ({
                    ...current,
                    [activeSessionId]: liveRun,
                }));

                if (isActiveRun(liveRun)) {
                    void watchRunRef.current(liveRun);
                } else {
                    void refreshSessionRef.current(activeSessionId);
                }
            } catch (error) {
                if (cancelled) {
                    return;
                }

                setLiveRuns((current) => ({
                    ...current,
                    [activeSessionId]: {
                        contents: [],
                        errorMessage:
                            error instanceof Error ? `无法恢复会话运行：${error.message}` : "无法恢复会话运行。",
                        id: `live-${requestedRunId}`,
                        runId: requestedRunId,
                        sessionId: activeSessionId,
                        status: "error",
                    },
                }));
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [activeSessionId, requestedRunId]);

    const navigateSessions = (sessionIds: string[]) => {
        router.replace(indexPageHref(INDEX_PAGE_KEYS.conversation, unique(sessionIds)), {
            scroll: false,
        });
    };

    const selectSession = (sessionId: string) => {
        navigateSessions([sessionId, ...openSessionIds.filter((item) => item !== sessionId)]);
    };

    const closeSession = (sessionId: string) => {
        navigateSessions(openSessionIds.filter((item) => item !== sessionId));
    };

    const createSession = async (): Promise<ConversationSession> => {
        if (creatingSession) {
            throw new Error("正在创建会话，请稍候。");
        }

        setCreatingSession(true);
        try {
            const session = await createConversationSession();
            queryClient.setQueryData(conversationSessionQueryKey(session.id), session);
            navigateSessions([session.id, ...openSessionIds]);
            void invalidateSessionLists();
            return session;
        } finally {
            setCreatingSession(false);
        }
    };

    const send = async ({ files, prompt }: ConversationSendInput): Promise<void> => {
        const content = prompt.trim();

        if (!content) {
            throw new Error("消息内容不能为空。");
        }
        if (running) {
            throw new Error("当前会话仍在生成，请先停止后再发送。");
        }
        if (activePhase) {
            throw new Error("上一条消息仍在发送，请稍候。");
        }

        const initialPhaseKey = activeSessionId ?? DRAFT_PHASE_KEY;
        let phaseKey = initialPhaseKey;
        setSubmissionPhase(initialPhaseKey, "creating");

        try {
            let sessionId = activeSessionId;
            let session = activeSessionQuery?.data;

            if (!sessionId) {
                session = await createConversationSession(conversationTitleFromPrompt(content));
                sessionId = session.id;
                phaseKey = sessionId;
                queryClient.setQueryData(conversationSessionQueryKey(sessionId), session);
                setSubmissionPhase(initialPhaseKey, undefined);
                setSubmissionPhase(sessionId, "uploading");
                navigateSessions([sessionId, ...openSessionIds]);
                void invalidateSessionLists();
            } else if (!session?.title || session.title.trim() === DEFAULT_SESSION_TITLE) {
                const title = conversationTitleFromPrompt(content);
                const renamed = await renameConversationSession(sessionId, title).catch(() => undefined);

                if (renamed) {
                    queryClient.setQueryData(conversationSessionQueryKey(sessionId), renamed);
                }
            }

            setSubmissionPhase(sessionId, files.length > 0 ? "uploading" : "starting");
            const assetIds = files.length > 0 ? await uploadConversationAssets(files) : [];

            setSubmissionPhase(sessionId, "starting");
            const run = await startConversationRun({
                assetIds,
                content,
                sessionId,
            });
            const liveRun = createConversationLiveRun(run);

            setLiveRuns((current) => ({
                ...current,
                [liveRun.sessionId]: liveRun,
            }));
            setSubmissionPhase(sessionId, undefined);
            void refreshSession(sessionId);
            void watchRun(liveRun);
        } finally {
            setSubmissionPhase(initialPhaseKey, undefined);
            setSubmissionPhase(phaseKey, undefined);
        }
    };

    const stop = async (): Promise<void> => {
        if (!activeSessionId || !activeLiveRun || !isActiveRun(activeLiveRun)) {
            return;
        }

        await cancelConversationRun(activeLiveRun.runId);
        subscriptionsRef.current.get(activeLiveRun.runId)?.abort();
        setLiveRuns((current) => {
            const latest = current[activeSessionId];

            if (!latest || latest.runId !== activeLiveRun.runId) {
                return current;
            }

            return {
                ...current,
                [activeSessionId]: {
                    ...latest,
                    contents: latest.contents.map((item) => ({ ...item, status: "stop" })),
                    status: "stop",
                },
            };
        });
        void refreshSession(activeSessionId);
    };

    const decideApproval = async (
        approvalId: string,
        toolCallId: string,
        decision: "approve" | "reject",
    ): Promise<void> => {
        if (!activeSessionId || !activeLiveRun || !isActiveRun(activeLiveRun)) {
            throw new Error("当前会话没有等待处理的工具审批。");
        }

        await decideConversationApproval(activeLiveRun.runId, approvalId, decision);
        setLiveRuns((current) => {
            const latest = current[activeSessionId];

            if (!latest || latest.runId !== activeLiveRun.runId) {
                return current;
            }

            return {
                ...current,
                [activeSessionId]: applyConversationApprovalDecision(latest, toolCallId, decision),
            };
        });
    };

    const loadOlder = async () => {
        if (!messagesQuery.hasNextPage || messagesQuery.isFetchingNextPage) {
            return;
        }

        await messagesQuery.fetchNextPage();
    };

    const retryMessages = () => {
        void messagesQuery.refetch();
    };

    async function watchRun(run: ConversationLiveRun) {
        const sessionId = run.sessionId;
        let lastEventId: string | undefined;
        let finished = false;

        for (let attempt = 0; attempt <= MAX_EVENT_RECONNECTS; attempt += 1) {
            const controller = new AbortController();
            let terminal = false;

            subscriptionsRef.current.set(run.runId, controller);
            try {
                await subscribeAgentRunEvents(run.runId, {
                    lastEventId,
                    onEvent: (event) => {
                        if (event.type && TERMINAL_EVENT_TYPES.has(event.type)) {
                            terminal = true;
                        }

                        setLiveRuns((current) => {
                            const latest = current[sessionId];

                            if (!latest || latest.runId !== run.runId) {
                                return current;
                            }

                            return {
                                ...current,
                                [sessionId]: applyConversationRunEvent(latest, event),
                            };
                        });
                    },
                    onEventId: (eventId) => {
                        lastEventId = eventId;
                    },
                    signal: controller.signal,
                });
            } catch (error) {
                if (controller.signal.aborted) {
                    break;
                }
                if (attempt >= MAX_EVENT_RECONNECTS) {
                    await reconcileRun(sessionId, run.runId, error);
                    break;
                }
                await delay(EVENT_RECONNECT_DELAY_MS * (attempt + 1));
                continue;
            } finally {
                if (subscriptionsRef.current.get(run.runId) === controller) {
                    subscriptionsRef.current.delete(run.runId);
                }
            }

            if (terminal) {
                finished = true;
                break;
            }

            try {
                const status = await getConversationRunStatus(run.runId);

                if (isTerminalRunStatus(status.status)) {
                    setLiveRuns((current) => {
                        const latest = current[sessionId];

                        if (!latest || latest.runId !== run.runId) {
                            return current;
                        }

                        return {
                            ...current,
                            [sessionId]: liveRunFromStatus(latest, status),
                        };
                    });
                    finished = true;
                    break;
                }
            } catch {
                // 状态查询失败时继续按事件游标重连。
            }

            if (attempt >= MAX_EVENT_RECONNECTS) {
                await reconcileRun(sessionId, run.runId, new Error("运行事件流多次中断。"));
                break;
            }
            await delay(EVENT_RECONNECT_DELAY_MS * (attempt + 1));
        }

        if (finished) {
            void refreshSession(sessionId);
        }
    }

    async function reconcileRun(sessionId: string, runId: string, error: unknown) {
        try {
            const run = await getConversationRunStatus(runId);
            setLiveRuns((current) => {
                const latest = current[sessionId];

                if (!latest || latest.runId !== runId) {
                    return current;
                }

                return {
                    ...current,
                    [sessionId]: liveRunFromStatus(latest, run),
                };
            });
        } catch {
            setLiveRuns((current) => {
                const latest = current[sessionId];

                if (!latest || latest.runId !== runId) {
                    return current;
                }

                return {
                    ...current,
                    [sessionId]: {
                        ...latest,
                        errorMessage:
                            error instanceof Error
                                ? `无法继续接收运行事件：${error.message}`
                                : "无法继续接收运行事件，请刷新会话。",
                        status: "error",
                    },
                };
            });
        }
    }

    const refreshSession = (sessionId: string) => {
        void queryClient.invalidateQueries({
            exact: true,
            queryKey: conversationMessagesQueryKey(sessionId),
        });
        void queryClient.invalidateQueries({
            exact: true,
            queryKey: conversationSessionQueryKey(sessionId),
        });
        return invalidateSessionLists();
    };

    const invalidateSessionLists = () =>
        Promise.all([
            queryClient.invalidateQueries({ queryKey: CONVERSATION_SESSIONS_QUERY_KEY }),
            queryClient.invalidateQueries({ queryKey: CONVERSATION_SUMMARY_QUERY_KEY }),
        ]);

    const setSubmissionPhase = (key: string, phase: ConversationSubmissionPhase | undefined) => {
        setSubmissionPhases((current) => {
            const next = { ...current };

            if (phase) {
                next[key] = phase;
            } else {
                delete next[key];
            }

            return next;
        });
    };

    watchRunRef.current = watchRun;
    refreshSessionRef.current = refreshSession;

    return {
        activePhase,
        activeSession: activeSessionQuery?.data,
        activeSessionError: activeSessionQuery?.error,
        activeSessionId,
        closeSession,
        createSession,
        creatingSession,
        decideApproval,
        hasOlder: Boolean(messagesQuery.hasNextPage),
        isFetchingOlder: messagesQuery.isFetchingNextPage,
        isLoadingMessages: loadingSession || (Boolean(activeSessionId) && messagesQuery.isPending),
        loadOlder,
        messages,
        messagesError: messagesQuery.error,
        latestActivity,
        reloadMessages: retryMessages,
        running,
        selectSession,
        send,
        stop,
        submissionLabel: phaseLabel(activePhase),
        tabs,
    };
}

export type ConversationController = ReturnType<typeof useConversationController>;

function normalizeMessages(newestFirst: AgentMessage[]): AgentMessage[] {
    const seen = new Set<string>();
    const chronological = [...newestFirst].reverse();

    return chronological.filter((message) => {
        if (!message.id || seen.has(message.id)) {
            return false;
        }

        seen.add(message.id);
        return true;
    });
}

function latestConversationActivity(messages: ConversationTimelineMessage[]): ActivityContent | undefined {
    for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
        const contents = messages[messageIndex].contents;

        for (let contentIndex = contents.length - 1; contentIndex >= 0; contentIndex -= 1) {
            const content = contents[contentIndex];

            if (content.type === "activity") {
                return content;
            }
        }
    }

    return undefined;
}

function isActiveRun(run: ConversationLiveRun | undefined): boolean {
    return run?.status === "pending" || run?.status === "streaming";
}

function isTerminalRunStatus(status: string | undefined): boolean {
    return status === "completed" || status === "failed" || status === "cancelled";
}

function liveRunFromStatus(current: ConversationLiveRun, run: AgentRun): ConversationLiveRun {
    switch (run.status) {
        case "completed":
            return {
                ...current,
                outputMessageId: run.outputMessageId ?? current.outputMessageId,
                status: "complete",
            };
        case "failed":
            return {
                ...current,
                errorMessage: run.errorMessage || "Agent 运行失败。",
                status: "error",
            };
        case "cancelled":
            return {
                ...current,
                contents: current.contents.map((item) => ({ ...item, status: "stop" })),
                status: "stop",
            };
        default:
            return { ...current, status: "streaming" };
    }
}

function phaseLabel(phase: ConversationSubmissionPhase | undefined): string | undefined {
    switch (phase) {
        case "creating":
            return "正在创建会话...";
        case "uploading":
            return "正在上传附件...";
        case "starting":
            return "正在启动运行...";
        default:
            return undefined;
    }
}

function unique(values: string[]): string[] {
    return [...new Set(values.filter(Boolean))];
}

function delay(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
        setTimeout(resolve, milliseconds);
    });
}
