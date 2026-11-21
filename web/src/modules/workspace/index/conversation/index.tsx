import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { useChat } from "@ai-sdk/react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Square } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";

import { ChatComposer } from "../components/chat-composer";
import { PudansChatTransport, type PudansUIMessage, toUiMessages } from "../lib/ai-chat-transport";
import {
  type ChatGenerationKind,
  type ChatMessage,
  type ChatRequest,
  branchConversation,
  confirmChatTool,
  fetchChatConversation,
  fetchChatMessages,
  fetchChatModes,
  interruptChatRun,
  resolveChatError,
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

export function ChatConversationView({ conversationId }: ChatConversationViewProps) {
  return <ChatConversationPane key={conversationId} conversationId={conversationId} />;
}

function ChatConversationPane({ conversationId }: ChatConversationViewProps) {
  const id = Number(conversationId);
  const valid = Number.isInteger(id) && id > 0;
  const router = useRouter();
  const queryClient = useQueryClient();
  const [modeOverride, setModeOverride] = useState<string | undefined>(undefined);
  const [modelOverride, setModelOverride] = useState<string | undefined>(undefined);
  const [routeGroupOverride, setRouteGroupOverride] = useState<string | null | undefined>(
    undefined,
  );
  const [kbCodes, setKbCodes] = useState<string[]>([]);
  const [generation, setGeneration] = useState<ChatGenerationProgress | null>(null);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [liveDone, setLiveDone] = useState(false);
  const sendingRef = useRef(false);
  const generatingRef = useRef(false);
  const mountedRef = useRef(true);
  const pendingStartedRef = useRef(false);
  const lastAssistantIdRef = useRef(0);

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

  const transport = useMemo(
    () =>
      new PudansChatTransport({
        conversationId: id,
        mode,
        routeGroup,
        model: modelOverride,
        kbCodes: kbCodes.length > 0 ? kbCodes : undefined,
        agentId: conversationQuery.data?.agentId,
      }),
    [conversationQuery.data?.agentId, id, kbCodes, mode, modelOverride, routeGroup],
  );

  const chat = useChat<PudansUIMessage>({
    transport,
    messages: [],
    throttle: 50,
    sendAutomaticallyWhen: () => false,
    onFinish: ({ message }) => {
      void (async () => {
        await queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
        await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
        const refreshed = queryClient.getQueryData<ChatMessage[]>(["chat-messages", id]) ?? [];
        const persisted = refreshed.some(
          (item) => item.role === "assistant" && item.id > lastAssistantIdRef.current,
        );
        if (mountedRef.current) {
          if (persisted || message.metadata?.partial) {
            setLiveDone(true);
          } else {
            setRequestError("流式连接已中断，请重试。");
          }
        }
        if (message.metadata?.partial) {
          toast.warning("回复未完整结束，已保留已生成的内容。");
        }
      })();
    },
    onError: (error) => {
      void queryClient.invalidateQueries({ queryKey: ["chat-messages", id] });
      if (mountedRef.current) {
        toast.error(resolveChatError(error));
      }
    },
  });

  useEffect(
    () => () => {
      mountedRef.current = false;
    },
    [],
  );

  const handleApproval = useCallback(
    (approvalId: string, approved: boolean) => {
      const runId = transport.runIdFor(approvalId);
      if (!runId) {
        toast.error("工具确认信息已失效，请重新发送。");
        return;
      }
      void confirmChatTool(runId, approvalId, approved).catch(() => {
        toast.error("工具确认失败，请重试。");
      });
      void chat.addToolApprovalResponse({ id: approvalId, approved });
    },
    [chat, transport],
  );

  const handleStop = useCallback(() => {
    const runId = transport.runId;
    if (runId) {
      void interruptChatRun(runId).catch(() => {
        toast.error("停止生成失败，请重试。");
      });
    }
    chat.stop();
  }, [chat, transport]);

  const sendPrompt = useCallback(
    async (request: StreamRequest) => {
      if (!valid || sendingRef.current || generatingRef.current) {
        return;
      }
      if (chat.status === "streaming" || chat.status === "submitted") {
        return;
      }
      sendingRef.current = true;
      setRequestError(null);
      setLiveDone(false);
      chat.clearError();
      const cached = queryClient.getQueryData<ChatMessage[]>(["chat-messages", id]) ?? [];
      lastAssistantIdRef.current = cached
        .filter((message) => message.role === "assistant")
        .reduce((max, message) => Math.max(max, message.id), 0);
      try {
        const body: Partial<ChatRequest> = {
          conversationId: id,
          mode: request.mode ?? mode,
          routeGroup: request.routeGroup ?? routeGroup,
          model: request.model ?? modelOverride,
          kbCodes: request.kbCodes ?? (kbCodes.length > 0 ? kbCodes : undefined),
          attachments: request.attachments.map((attachment) => ({
            storageFileId: attachment.fileId,
            type: attachment.type,
            name: attachment.name,
            contentType: attachment.contentType,
            size: attachment.size,
          })),
          command: request.command,
          commandInput: request.commandInput,
          idempotencyKey: crypto.randomUUID(),
        };
        if (request.regenerate) {
          const lastAssistant = [...cached]
            .reverse()
            .find((message) => message.role === "assistant");
          const history = lastAssistant
            ? cached.filter((message) => message.id !== lastAssistant.id)
            : cached;
          if (lastAssistant) {
            queryClient.setQueryData(["chat-messages", id], history);
          }
          chat.setMessages(toUiMessages(history));
          await chat.regenerate({ body });
        } else {
          chat.setMessages([]);
          await chat.sendMessage({ text: request.prompt }, { body });
        }
      } catch (error) {
        const message = resolveChatError(error);
        if (mountedRef.current) {
          setRequestError(message);
        }
        toast.error(message);
      } finally {
        sendingRef.current = false;
      }
    },
    [chat, id, kbCodes, mode, modelOverride, queryClient, routeGroup, valid],
  );

  const runGeneration = useCallback(
    async (
      kind: ChatGenerationKind,
      prompt: string,
      format?: "MARKDOWN" | "HTML",
      routeGroupArgument?: string,
    ) => {
      if (!valid || sendingRef.current || generatingRef.current) {
        return;
      }
      if (chat.status === "streaming" || chat.status === "submitted") {
        return;
      }
      generatingRef.current = true;
      setRequestError(null);
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
        generatingRef.current = false;
        if (mountedRef.current) {
          setGeneration(null);
        }
      }
    },
    [chat.status, id, queryClient, routeGroup, valid],
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
        router.push(`/chat/${branch.id}`);
        return true;
      } catch (error) {
        toast.error(resolveChatError(error));
        return false;
      }
    },
    [id, kbCodes, mode, modelOverride, router, queryClient, routeGroupOverride],
  );

  const chatError = chat.error ? resolveChatError(chat.error) : null;
  const errorMessage = requestError ?? chatError;
  const chatBusy = chat.status === "streaming" || chat.status === "submitted";
  const busy = chatBusy || generation !== null;

  if (!valid) {
    return (
      <div className="flex h-svh min-h-0 flex-col">
        <ConversationHeader title="会话不存在" />
        <div className="flex flex-1 flex-col items-center justify-center gap-3 px-6 text-center">
          <p className="text-sm text-muted-foreground">会话标识无效。</p>
          <Link href="/" className="text-sm font-medium text-primary hover:underline">
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
          <Link href="/" className="text-sm font-medium text-primary hover:underline">
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
        liveMessages={liveDone ? [] : chat.messages}
        liveStatus={chat.status}
        loading={messagesQuery.isPending}
        error={errorMessage}
        generation={generation}
        onRetry={retryMessage}
        onEditBranch={editBranch}
        onApproval={handleApproval}
      />
      <div className="shrink-0 px-6 pb-3">
        <div className="mx-auto w-full max-w-3xl space-y-2">
          {chatBusy ? (
            <div className="flex justify-center">
              <Button type="button" variant="outline" size="sm" onClick={handleStop}>
                <Square />
                停止生成
              </Button>
            </div>
          ) : null}
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
            sending={busy}
            placeholder="继续对话，Enter 发送，Shift+Enter 换行"
          />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}
