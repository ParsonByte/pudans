import { useCallback, useEffect, useRef, useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { toast } from "sonner";

import { ChatComposer } from "../components/chat-composer";
import {
  type ChatAttachmentRef,
  type ChatMessage,
  type ChatStreamDone,
  type ChatStreamError,
  fetchChatConversation,
  fetchChatMessages,
  fetchChatModes,
  resolveChatError,
  streamChat,
} from "../lib/chat-api";
import { takePendingChat } from "../lib/pending-chat";
import { ConversationHeader } from "./components/conversation-header";
import { ConversationMessages } from "./components/conversation-messages";

type ChatConversationViewProps = {
  conversationId: string;
};

export function ChatConversationView({ conversationId }: ChatConversationViewProps) {
  const id = Number(conversationId);
  const valid = Number.isInteger(id) && id > 0;
  const queryClient = useQueryClient();
  const [modeOverride, setModeOverride] = useState<string | undefined>(undefined);
  const [optimisticMessage, setOptimisticMessage] = useState<{
    content: string;
    attachments: ChatAttachmentRef[];
  } | null>(null);
  const [assistantDraft, setAssistantDraft] = useState("");
  const [reasoningDraft, setReasoningDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const sendingRef = useRef(false);
  const mountedRef = useRef(true);
  const pendingStartedRef = useRef(false);
  const streamIdRef = useRef(0);

  const modesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
  });
  const conversationQuery = useQuery({
    queryKey: ["chat-conversation", id],
    queryFn: () => fetchChatConversation(id),
    enabled: valid,
    retry: false,
  });
  const messagesQuery = useQuery({
    queryKey: ["chat-messages", id],
    queryFn: () => fetchChatMessages(id),
    enabled: valid,
    retry: false,
  });
  const modes = modesQuery.data ?? [];
  const mode = modeOverride ?? conversationQuery.data?.modeCode ?? modes[0]?.name;

  useEffect(
    () => () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    },
    [],
  );

  const sendPrompt = useCallback(
    async (prompt: string, modeOverride?: string, attachments: ChatAttachmentRef[] = []) => {
      if (!valid || sendingRef.current) {
        return;
      }
      sendingRef.current = true;
      const streamId = streamIdRef.current + 1;
      streamIdRef.current = streamId;
      setSending(true);
      setStreamError(null);
      setOptimisticMessage({ content: prompt, attachments });
      setAssistantDraft("");
      setReasoningDraft("");
      const cachedMessages = queryClient.getQueryData<ChatMessage[]>(["chat-messages", id]) ?? [];
      const lastAssistantId = cachedMessages
        .filter((message) => message.role === "assistant")
        .reduce((max, message) => Math.max(max, message.id), 0);
      const controller = new AbortController();
      controllerRef.current = controller;
      let completed = false;
      try {
        await streamChat(
          {
            conversationId: id,
            mode: modeOverride ?? mode,
            messages: [{ role: "user", content: prompt }],
            attachments: attachments.map((attachment) => ({
              storageFileId: attachment.fileId,
              type: attachment.type,
              name: attachment.name,
              contentType: attachment.contentType,
              size: attachment.size,
            })),
            idempotencyKey: crypto.randomUUID(),
          },
          {
            onDelta: (delta) => {
              if (mountedRef.current && delta.content) {
                setAssistantDraft((current) => current + delta.content);
              }
              if (mountedRef.current && delta.reasoning) {
                setReasoningDraft((current) => current + delta.reasoning);
              }
            },
            onError: (error: ChatStreamError) => {
              completed = true;
              const message = error.message || "生成失败，请稍后重试。";
              if (mountedRef.current && streamIdRef.current === streamId) {
                setStreamError(message);
                toast.error(message);
              }
            },
            onDone: (done: ChatStreamDone) => {
              completed = true;
              void (async () => {
                try {
                  await queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
                  await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
                } finally {
                  if (mountedRef.current && streamIdRef.current === streamId) {
                    setOptimisticMessage(null);
                    setAssistantDraft("");
                    setReasoningDraft("");
                    if (done.partial) {
                      toast.warning("回复未完整结束，已保留已生成的内容。");
                    }
                  }
                }
              })();
            },
          },
          controller.signal,
        );
        if (!completed && !controller.signal.aborted && mountedRef.current) {
          // 流在 done 事件前关闭时，从会话历史补回已经落库的回复。
          const refreshed = await queryClient.fetchQuery({
            queryKey: ["chat-messages", id],
            queryFn: () => fetchChatMessages(id),
            staleTime: 0,
          });
          const persisted = refreshed.some(
            (message) => message.role === "assistant" && message.id > lastAssistantId,
          );
          if (persisted) {
            await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
            if (mountedRef.current && streamIdRef.current === streamId) {
              setOptimisticMessage(null);
              setAssistantDraft("");
              setReasoningDraft("");
            }
          } else {
            const message = "流式连接已中断，请重试。";
            setStreamError(message);
            toast.error(message);
          }
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          const message = resolveChatError(error);
          if (mountedRef.current) {
            setStreamError(message);
          }
          toast.error(message);
        }
        throw error;
      } finally {
        sendingRef.current = false;
        if (mountedRef.current) {
          setSending(false);
        }
        controllerRef.current = null;
      }
    },
    [id, mode, queryClient, valid],
  );

  useEffect(() => {
    if (!valid || pendingStartedRef.current) {
      return;
    }
    pendingStartedRef.current = true;
    const pending = takePendingChat(id);
    if (!pending) {
      return;
    }
    queueMicrotask(() => {
      if (!mountedRef.current) {
        return;
      }
      void sendPrompt(pending.prompt, pending.mode, pending.attachments).catch(() => undefined);
    });
  }, [id, sendPrompt, valid]);

  if (!valid) {
    return (
      <div className="flex h-svh min-h-0 flex-col">
        <ConversationHeader title="会话不存在" />
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <p className="text-sm text-muted-foreground">会话标识无效。</p>
          <Link to="/" className="text-sm font-medium text-primary hover:underline">
            返回新会话
          </Link>
        </div>
      </div>
    );
  }

  if (conversationQuery.isError) {
    return (
      <div className="flex h-svh min-h-0 flex-col">
        <ConversationHeader title="会话不可用" />
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <p className="text-sm text-muted-foreground">
            {resolveChatError(conversationQuery.error)}
          </p>
          <Link to="/" className="text-sm font-medium text-primary hover:underline">
            返回新会话
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-svh min-h-0 flex-col">
      <ConversationHeader title={conversationQuery.data?.title || "新的对话"} />
      <ConversationMessages
        messages={messagesQuery.data ?? []}
        loading={messagesQuery.isPending}
        error={streamError}
        optimisticMessage={optimisticMessage}
        assistantDraft={assistantDraft}
        reasoningDraft={reasoningDraft}
        sending={sending}
      />
      <div className="shrink-0 px-6 pb-3">
        <div className="mx-auto w-full max-w-3xl space-y-2">
          <ChatComposer
            modes={modes}
            mode={mode}
            onModeChange={setModeOverride}
            onSend={(prompt, attachments) => sendPrompt(prompt, undefined, attachments)}
            sending={sending}
            placeholder="继续对话，Enter 发送，Shift+Enter 换行"
          />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}
