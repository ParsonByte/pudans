import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { toast } from "sonner";

import { ChatComposer } from "../components/chat-composer";
import {
  type ChatArtifact,
  type ChatAttachmentRef,
  type ChatGenerationKind,
  type ChatMessage,
  type ChatStreamDone,
  type ChatStreamDraft,
  type ChatStreamError,
  type ChatToolConfirm,
  branchConversation,
  confirmChatTool,
  fetchChatConversation,
  fetchChatMessages,
  fetchChatModes,
  resolveChatError,
  streamChat,
} from "../lib/chat-api";
import { type ChatRequestBody, parseChatCommand } from "../lib/chat-functions";
import { type ChatGenerationProgress, createChatArtifact } from "../lib/chat-generation";
import {
  useChatAgentsQuery,
  useChatCommandsQuery,
  useChatKnowledgeBasesQuery,
  useChatModelsQuery,
  useChatRouteGroupsQuery,
} from "../lib/chat-options";
import { setPendingChat, takePendingChat } from "../lib/pending-chat";
import { ConversationHeader } from "./components/conversation-header";
import { ConversationMessages } from "./components/conversation-messages";
import { ToolConfirmDialog } from "./components/tool-confirm-dialog";

type ChatConversationViewProps = {
  conversationId: string;
};

type StreamRequest = ChatRequestBody & {
  mode?: string;
  model?: string;
  routeGroup?: string;
  kbCodes?: string[];
  regenerate?: boolean;
};

const EMPTY_DRAFT: ChatStreamDraft = {
  content: "",
  reasoning: "",
  toolCalls: [],
  toolResults: [],
  artifacts: [],
  stage: null,
};

export function ChatConversationView({ conversationId }: ChatConversationViewProps) {
  return <ChatConversationPane key={conversationId} conversationId={conversationId} />;
}

function ChatConversationPane({ conversationId }: ChatConversationViewProps) {
  const id = Number(conversationId);
  const valid = Number.isInteger(id) && id > 0;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [modeOverride, setModeOverride] = useState<string | undefined>(undefined);
  const [modelOverride, setModelOverride] = useState<string | undefined>(undefined);
  const [routeGroupOverride, setRouteGroupOverride] = useState<string | null | undefined>(
    undefined,
  );
  const [kbCodes, setKbCodes] = useState<string[]>([]);
  const [optimisticMessage, setOptimisticMessage] = useState<{
    content: string;
    attachments: ChatAttachmentRef[];
  } | null>(null);
  const [draft, setDraft] = useState<ChatStreamDraft>(EMPTY_DRAFT);
  const [generation, setGeneration] = useState<ChatGenerationProgress | null>(null);
  const [sending, setSending] = useState(false);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [pendingConfirm, setPendingConfirm] = useState<ChatToolConfirm | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const sendingRef = useRef(false);
  const mountedRef = useRef(true);
  const pendingStartedRef = useRef(false);
  const pendingConfirmRef = useRef<ChatToolConfirm | null>(null);
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
  const agentsQuery = useChatAgentsQuery();
  const modelsQuery = useChatModelsQuery();
  const routeGroupsQuery = useChatRouteGroupsQuery();
  const knowledgeQuery = useChatKnowledgeBasesQuery();
  const commandsQuery = useChatCommandsQuery(conversationQuery.data?.agentId);
  const modes = modesQuery.data ?? [];
  const agents = agentsQuery.data ?? [];
  const models = modelsQuery.data ?? [];
  const routeGroups = routeGroupsQuery.data ?? [];
  const knowledgeBases = knowledgeQuery.data ?? [];
  const commands = useMemo(() => commandsQuery.data ?? [], [commandsQuery.data]);
  const mode = modeOverride ?? conversationQuery.data?.modeCode ?? modes[0]?.name;
  const routeGroup =
    routeGroupOverride === undefined
      ? conversationQuery.data?.routeGroup
      : (routeGroupOverride ?? undefined);

  useEffect(
    () => () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    },
    [],
  );

  const updatePendingConfirm = useCallback((next: ChatToolConfirm | null) => {
    pendingConfirmRef.current = next;
    setPendingConfirm(next);
  }, []);

  const resolveToolConfirm = useCallback(
    (approved: boolean) => {
      const current = pendingConfirmRef.current;
      if (!current?.runId || !current.toolCallId) {
        return;
      }
      updatePendingConfirm(null);
      void confirmChatTool(current.runId, current.toolCallId, approved).catch(() => {
        if (mountedRef.current) {
          toast.error("工具确认失败，请重试。");
        }
      });
    },
    [updatePendingConfirm],
  );

  const sendPrompt = useCallback(
    async (request: StreamRequest) => {
      if (!valid || sendingRef.current) {
        return;
      }
      sendingRef.current = true;
      const streamId = streamIdRef.current + 1;
      streamIdRef.current = streamId;
      setSending(true);
      setStreamError(null);
      setDraft(EMPTY_DRAFT);
      if (!request.regenerate) {
        setOptimisticMessage({ content: request.prompt, attachments: request.attachments });
      }
      const cachedMessages = queryClient.getQueryData<ChatMessage[]>(["chat-messages", id]) ?? [];
      const lastAssistantId = cachedMessages
        .filter((message) => message.role === "assistant")
        .reduce((max, message) => Math.max(max, message.id), 0);
      if (request.regenerate) {
        const lastAssistant = [...cachedMessages]
          .reverse()
          .find((message) => message.role === "assistant");
        if (lastAssistant) {
          queryClient.setQueryData(
            ["chat-messages", id],
            cachedMessages.filter((message) => message.id !== lastAssistant.id),
          );
        }
      }
      const controller = new AbortController();
      controllerRef.current = controller;
      let completed = false;
      try {
        await streamChat(
          {
            conversationId: id,
            mode: request.mode ?? mode,
            routeGroup: request.routeGroup ?? routeGroup,
            model: request.model ?? modelOverride,
            kbCodes: request.kbCodes ?? (kbCodes.length > 0 ? kbCodes : undefined),
            messages: [{ role: "user", content: request.prompt }],
            attachments: request.attachments.map((attachment) => ({
              storageFileId: attachment.fileId,
              type: attachment.type,
              name: attachment.name,
              contentType: attachment.contentType,
              size: attachment.size,
            })),
            command: request.command,
            commandInput: request.commandInput,
            regenerate: request.regenerate,
            idempotencyKey: crypto.randomUUID(),
          },
          {
            onDelta: (delta) => {
              if (!mountedRef.current) {
                return;
              }
              setDraft((current) => ({
                ...current,
                content: current.content + (delta.content ?? ""),
                reasoning: current.reasoning + (delta.reasoning ?? ""),
                stage: null,
              }));
            },
            onToolCall: (call) => {
              if (mountedRef.current) {
                setDraft((current) => ({ ...current, toolCalls: [...current.toolCalls, call] }));
              }
            },
            onToolResult: (result) => {
              if (mountedRef.current) {
                setDraft((current) => ({
                  ...current,
                  toolResults: [...current.toolResults, result],
                }));
              }
            },
            onToolConfirm: (confirm) => {
              if (mountedRef.current && streamIdRef.current === streamId) {
                updatePendingConfirm(confirm);
              }
            },
            onArtifact: (artifact) => {
              if (mountedRef.current) {
                setDraft((current) => ({
                  ...current,
                  artifacts: mergeArtifact(current.artifacts, artifact),
                }));
              }
            },
            onStage: (stage) => {
              if (mountedRef.current) {
                setDraft((current) => ({ ...current, stage }));
              }
            },
            onError: (error: ChatStreamError) => {
              completed = true;
              updatePendingConfirm(null);
              void queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
              const message = error.message || "生成失败，请稍后重试。";
              if (mountedRef.current && streamIdRef.current === streamId) {
                setStreamError(message);
                toast.error(message);
              }
            },
            onDone: (done: ChatStreamDone) => {
              completed = true;
              updatePendingConfirm(null);
              void (async () => {
                try {
                  await queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
                  await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
                } finally {
                  if (mountedRef.current && streamIdRef.current === streamId) {
                    setOptimisticMessage(null);
                    setDraft(EMPTY_DRAFT);
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
              setDraft(EMPTY_DRAFT);
            }
          } else {
            const message = "流式连接已中断，请重试。";
            setStreamError(message);
            toast.error(message);
          }
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          void queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
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
          updatePendingConfirm(null);
        }
        controllerRef.current = null;
      }
    },
    [id, kbCodes, mode, modelOverride, queryClient, routeGroup, updatePendingConfirm, valid],
  );

  const runGeneration = useCallback(
    async (
      kind: ChatGenerationKind,
      prompt: string,
      format?: "MARKDOWN" | "HTML",
      routeGroupArgument?: string,
    ) => {
      if (!valid || sendingRef.current) {
        return;
      }
      sendingRef.current = true;
      setSending(true);
      setStreamError(null);
      setOptimisticMessage({ content: prompt, attachments: [] });
      setGeneration({ kind, prompt, stage: "准备生成", progress: 5 });
      try {
        await createChatArtifact({
          conversationId: id,
          kind,
          prompt,
          format,
          routeGroup: routeGroupArgument ?? routeGroup,
          onProgress: (progress) => {
            if (mountedRef.current) {
              setGeneration(progress);
            }
          },
        });
        await queryClient.refetchQueries({ queryKey: ["chat-messages", id] });
        await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
        if (mountedRef.current) {
          toast.success("生成完成，已保存到会话。");
        }
      } catch (error) {
        const message = resolveChatError(error);
        if (mountedRef.current) {
          toast.error(message);
        }
      } finally {
        sendingRef.current = false;
        if (mountedRef.current) {
          setSending(false);
          setGeneration(null);
          setOptimisticMessage(null);
        }
      }
    },
    [id, queryClient, routeGroup, valid],
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
      if (pending.generation) {
        void runGeneration(
          pending.generation.kind,
          pending.prompt,
          pending.generation.format,
          pending.routeGroup,
        ).catch(() => undefined);
        return;
      }
      const parsed = parseChatCommand(pending.prompt, commands);
      void sendPrompt({
        prompt: pending.prompt,
        attachments: pending.attachments,
        mode: pending.mode,
        model: pending.model,
        routeGroup: pending.routeGroup,
        kbCodes: pending.kbCodes,
        command: pending.command ?? parsed.command,
        commandInput: pending.commandInput ?? parsed.commandInput,
      }).catch(() => undefined);
    });
  }, [commands, id, runGeneration, sendPrompt, valid]);

  const retryMessage = useCallback(
    (message: ChatMessage) => {
      const history = queryClient.getQueryData<ChatMessage[]>(["chat-messages", id]) ?? [];
      const lastUser = [...history]
        .reverse()
        .find((item) => item.role === "user" && item.content?.trim());
      if (!lastUser) {
        toast.error("没有可重试的用户消息。");
        return;
      }
      const lastAssistant = [...history].reverse().find((item) => item.role === "assistant");
      if (lastAssistant && lastAssistant.id !== message.id) {
        toast.error("只能重试最后一条回复。");
        return;
      }
      void sendPrompt({
        prompt: lastUser.content ?? "",
        attachments: [],
        regenerate: true,
      }).catch(() => undefined);
    },
    [id, queryClient, sendPrompt],
  );

  const editBranch = useCallback(
    async (message: ChatMessage, content: string): Promise<boolean> => {
      try {
        const branch = await branchConversation(id, message.id);
        await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
        setPendingChat({
          conversationId: branch.id,
          prompt: content,
          mode: branch.modeCode ?? mode,
          attachments: [],
          model: modelOverride,
          routeGroup: routeGroupOverride ?? branch.routeGroup ?? undefined,
          kbCodes,
        });
        await navigate({
          to: "/chat/$conversationId",
          params: { conversationId: String(branch.id) },
        });
        return true;
      } catch (error) {
        toast.error(resolveChatError(error));
        return false;
      }
    },
    [id, kbCodes, mode, modelOverride, navigate, queryClient, routeGroupOverride],
  );

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
      <ToolConfirmDialog
        confirm={pendingConfirm}
        onApprove={() => resolveToolConfirm(true)}
        onDeny={() => resolveToolConfirm(false)}
      />
      <ConversationHeader title={conversationQuery.data?.title || "新的对话"} />
      <ConversationMessages
        messages={messagesQuery.data ?? []}
        loading={messagesQuery.isPending}
        error={streamError}
        optimisticMessage={optimisticMessage}
        draft={draft}
        sending={sending}
        generation={generation}
        onRetry={retryMessage}
        onEditBranch={editBranch}
      />
      <div className="shrink-0 px-6 pb-3">
        <div className="mx-auto w-full max-w-3xl space-y-2">
          <ChatComposer
            modes={modes}
            mode={mode}
            onModeChange={setModeOverride}
            agents={agents}
            agentsLoading={agentsQuery.isPending}
            agent={conversationQuery.data?.agentId}
            agentLocked
            models={models}
            modelsLoading={modelsQuery.isPending}
            model={modelOverride}
            onModelChange={setModelOverride}
            routeGroups={routeGroups}
            routeGroupsLoading={routeGroupsQuery.isPending}
            routeGroup={routeGroup}
            onRouteGroupChange={(value) => setRouteGroupOverride(value ?? null)}
            kbCodes={kbCodes}
            knowledgeBases={knowledgeBases}
            knowledgeLoading={knowledgeQuery.isPending}
            knowledgeError={
              knowledgeQuery.error ? resolveChatError(knowledgeQuery.error) : undefined
            }
            onRetryKnowledge={() => void knowledgeQuery.refetch()}
            onKbCodesChange={setKbCodes}
            commands={commands}
            commandsLoading={commandsQuery.isFetching}
            onSend={(request) => sendPrompt(request)}
            onGenerate={(input) => {
              void runGeneration(input.kind, input.prompt, input.format);
            }}
            sending={sending}
            placeholder="继续对话，Enter 发送，Shift+Enter 换行"
          />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}

function mergeArtifact(list: ChatArtifact[], artifact: ChatArtifact): ChatArtifact[] {
  const index = list.findIndex(
    (item) =>
      (artifact.fileId && item.fileId === artifact.fileId) ||
      (artifact.url && item.url === artifact.url) ||
      (artifact.name && item.kind === artifact.kind && item.name === artifact.name),
  );
  if (index < 0) {
    return [...list, artifact];
  }
  const next = [...list];
  next[index] = { ...next[index], ...artifact };
  return next;
}
