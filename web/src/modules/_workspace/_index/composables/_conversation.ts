import {
    type AgentMessagePage,
    type AgentRun,
    type AgentRunEvent,
    type AgentRunStartRequest,
    type AgentSession,
    type AgentSessionSummary,
    type Asset,
    apiClient,
    axiosInstance,
} from "@pudans-dev/api-client";

import { csrfHeaders } from "@/modules/_auth/composables/_auth-login";
import { createStorageFileDownloadUrl } from "@/modules/_workspace/_console/composables/_storage";

import { uploadWorkspaceAssets } from "./_workspace-assets";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";
const CONVERSATION_UPLOAD_MODULE = "agent";

const CONVERSATION_QUERY_ROOT = ["index", "conversation"] as const;
export const CONVERSATION_SESSIONS_QUERY_KEY = [...CONVERSATION_QUERY_ROOT, "sessions"] as const;
export const CONVERSATION_SUMMARY_QUERY_KEY = [...CONVERSATION_QUERY_ROOT, "summary"] as const;

export type ConversationSession = AgentSession & { id: string };

export type ConversationMessagePage = AgentMessagePage;

export type ConversationAssetView = {
    asset: Asset;
    ancestors: Asset[];
};

export function conversationSessionQueryKey(sessionId: string) {
    return [...CONVERSATION_QUERY_ROOT, "session", sessionId] as const;
}

export function conversationMessagesQueryKey(sessionId: string) {
    return [...CONVERSATION_QUERY_ROOT, "messages", sessionId] as const;
}

export function conversationAssetQueryKey(assetId: string) {
    return [...CONVERSATION_QUERY_ROOT, "asset", assetId] as const;
}

export function conversationDownloadUrlQueryKey(storageFileId: string) {
    return [...CONVERSATION_QUERY_ROOT, "download-url", storageFileId] as const;
}

export async function listMyConversationSessions(limit = 20): Promise<ConversationSession[]> {
    const response = await apiClient.agentSession.listAgentSessions({ params: { limit } });

    return ensureSessions(response.data.items ?? []);
}

export async function getMyConversationSummary(): Promise<AgentSessionSummary> {
    return (await apiClient.agentSession.summarizeAgentSessions()).data;
}

export async function getConversationSession(sessionId: string): Promise<ConversationSession> {
    const response = await apiClient.agentSession.getAgentSession({ name: sessionId });

    if (!response.data.id) {
        throw new Error("会话响应缺少标识。");
    }

    return response.data as ConversationSession;
}

export async function createConversationSession(title?: string): Promise<ConversationSession> {
    const response = await apiClient.agentSession.createAgentSession(
        {
            agentSessionCreateRequest: {
                title: title?.trim() || undefined,
            },
        },
        { headers: csrfHeaders() },
    );

    if (!response.data.id) {
        throw new Error("创建会话响应缺少标识。");
    }

    return response.data as ConversationSession;
}

export async function renameConversationSession(sessionId: string, title: string): Promise<ConversationSession> {
    const response = await apiClient.agentSession.updateAgentSessionMetadata(
        {
            name: sessionId,
            agentSessionUpdateRequest: {
                title,
            },
        },
        { headers: csrfHeaders() },
    );

    if (!response.data.id) {
        throw new Error("更新会话响应缺少标识。");
    }

    return response.data as ConversationSession;
}

export async function listConversationMessages(
    sessionId: string,
    options: { before?: string; limit?: number } = {},
): Promise<ConversationMessagePage> {
    const response = await apiClient.agentSession.listAgentSessionMessages(
        { name: sessionId },
        {
            params: {
                before: options.before,
                limit: options.limit ?? 50,
            },
        },
    );

    return response.data;
}

export async function startConversationRun(input: AgentRunStartRequest): Promise<AgentRun> {
    const response = await apiClient.agentRun.startAgentRun(
        { agentRunStartRequest: input },
        { headers: csrfHeaders() },
    );

    if (!response.data.id) {
        throw new Error("启动运行响应缺少运行标识。");
    }

    return response.data;
}

export async function cancelConversationRun(runId: string): Promise<AgentRun> {
    return (await apiClient.agentRun.cancelAgentRun({ name: runId }, { headers: csrfHeaders() })).data;
}

export async function getConversationRunStatus(runId: string): Promise<AgentRun> {
    return (await apiClient.agentRun.getAgentRunStatus({ name: runId })).data;
}

export async function decideConversationApproval(runId: string, approvalId: string, decision: "approve" | "reject") {
    return (
        await apiClient.agentRun.decideAgentApproval(
            {
                name: runId,
                approvalName: approvalId,
                agentApprovalDecisionRequest: { decision },
            },
            { headers: csrfHeaders() },
        )
    ).data;
}

export async function uploadConversationAssets(files: readonly File[]): Promise<string[]> {
    return uploadWorkspaceAssets(files, {
        module: CONVERSATION_UPLOAD_MODULE,
        source: "chat",
    });
}

export async function getConversationAsset(assetId: string): Promise<ConversationAssetView> {
    return (await axiosInstance.get<ConversationAssetView>(`${API_ROOT}/assets/${encodeURIComponent(assetId)}/view`))
        .data;
}

export async function getConversationAssetDownloadUrl(storageFileId: string): Promise<string> {
    return createStorageFileDownloadUrl(storageFileId);
}

export type AgentRunEventSubscription = {
    lastEventId?: string;
    onEvent: (event: AgentRunEvent) => void | Promise<void>;
    onEventId?: (eventId: string) => void;
    signal?: AbortSignal;
};

export async function subscribeAgentRunEvents(runId: string, options: AgentRunEventSubscription): Promise<void> {
    const response = await fetch(`${API_ROOT}/agentruns/${encodeURIComponent(runId)}/events`, {
        credentials: "include",
        headers: streamHeaders(options.lastEventId),
        signal: options.signal,
    });

    if (!response.ok) {
        const detail = await response.text().catch(() => "");
        throw new Error(detail || `运行事件流连接失败（${response.status}）。`);
    }
    if (!response.body) {
        throw new Error("浏览器不支持读取运行事件流。");
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    try {
        while (true) {
            const { done, value } = await reader.read();

            if (done) {
                break;
            }

            buffer += decoder.decode(value, { stream: true });
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() ?? "";

            for (const frame of frames) {
                await dispatchFrame(frame, options);
            }
        }

        buffer += decoder.decode();
        if (buffer.trim()) {
            await dispatchFrame(buffer, options);
        }
    } finally {
        reader.releaseLock();
    }
}

function streamHeaders(lastEventId?: string): HeadersInit {
    const headers: Record<string, string> = {
        Accept: "text/event-stream",
        "Cache-Control": "no-cache",
        "X-Requested-With": "XMLHttpRequest",
    };

    if (typeof window !== "undefined") {
        const workspace = window.location.pathname.match(/^\/workspace\/([^/?#]+)/)?.[1];

        if (workspace) {
            headers["X-Pudans-Workspace"] = decodeURIComponent(workspace);
        }
    }
    if (lastEventId) {
        headers["Last-Event-ID"] = lastEventId;
    }

    return headers;
}

async function dispatchFrame(frame: string, options: AgentRunEventSubscription): Promise<void> {
    let eventName = "";
    const dataLines: string[] = [];

    for (const line of frame.split(/\r?\n/)) {
        if (!line || line.startsWith(":")) {
            continue;
        }
        if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
            continue;
        }
        if (line.startsWith("data:")) {
            dataLines.push(line.slice(5).trimStart());
        }
    }

    if (dataLines.length === 0) {
        return;
    }

    const parsed = JSON.parse(dataLines.join("\n")) as AgentRunEvent;
    const event = {
        ...parsed,
        type: parsed.type || eventName || undefined,
    };

    if (event.eventId) {
        options.onEventId?.(event.eventId);
    }
    await options.onEvent(event);
}

function ensureSessions(items: AgentSession[]): ConversationSession[] {
    return items.flatMap((session) => (session.id ? [session as ConversationSession] : []));
}
