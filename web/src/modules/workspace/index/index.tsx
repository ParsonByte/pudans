import { useState } from "react";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { PageHeader } from "@/components/layout/page-header";

import { ChatComposer } from "./components/chat-composer";
import {
  type ChatGenerationKind,
  createChatConversation,
  fetchChatModes,
  resolveChatError,
} from "./lib/chat-api";
import type { ChatRequestBody } from "./lib/chat-functions";
import {
  useChatAgentsQuery,
  useChatCommandsQuery,
  useChatKnowledgeBasesQuery,
  useChatModelsQuery,
  useChatRouteGroupsQuery,
} from "./lib/chat-options";
import { setPendingChat } from "./lib/pending-chat";

const TITLE_LIMIT = 80;

export function ChatNewView() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [modeOverride, setModeOverride] = useState<string | undefined>(undefined);
  const [agent, setAgent] = useState<string | undefined>(undefined);
  const [model, setModel] = useState<string | undefined>(undefined);
  const [routeGroup, setRouteGroup] = useState<string | undefined>(undefined);
  const [kbCodes, setKbCodes] = useState<string[]>([]);
  const [sending, setSending] = useState(false);
  const modesQuery = useQuery({
    queryKey: ["chat-modes"],
    queryFn: fetchChatModes,
    retry: false,
  });
  const agentsQuery = useChatAgentsQuery();
  const modelsQuery = useChatModelsQuery();
  const routeGroupsQuery = useChatRouteGroupsQuery();
  const knowledgeQuery = useChatKnowledgeBasesQuery();
  const commandsQuery = useChatCommandsQuery(agent);
  const modes = modesQuery.data ?? [];
  const mode = modeOverride ?? modes[0]?.name;

  const createConversation = async (prompt: string) => {
    const title = prompt.length <= TITLE_LIMIT ? prompt : prompt.slice(0, TITLE_LIMIT);
    const conversation = await createChatConversation({
      title,
      modeCode: mode,
      routeGroup,
      agentName: agent,
    });
    await queryClient.invalidateQueries({ queryKey: ["chat-conversations"] });
    return conversation;
  };

  const send = async (request: ChatRequestBody) => {
    if (sending) {
      return;
    }
    setSending(true);
    try {
      const conversation = await createConversation(request.prompt);
      setPendingChat({
        conversationId: conversation.id,
        prompt: request.prompt,
        mode,
        attachments: request.attachments,
        model,
        routeGroup,
        kbCodes,
        command: request.command,
        commandInput: request.commandInput,
      });
      await navigate({
        to: "/chat/$conversationId",
        params: { conversationId: String(conversation.id) },
      });
    } catch (error) {
      throw new Error(resolveChatError(error), { cause: error });
    } finally {
      setSending(false);
    }
  };

  const generate = async (input: {
    kind: ChatGenerationKind;
    prompt: string;
    format?: "MARKDOWN" | "HTML";
  }) => {
    if (sending) {
      return;
    }
    setSending(true);
    try {
      const conversation = await createConversation(input.prompt);
      setPendingChat({
        conversationId: conversation.id,
        prompt: input.prompt,
        mode,
        attachments: [],
        model,
        routeGroup,
        kbCodes,
        generation: { kind: input.kind, format: input.format },
      });
      await navigate({
        to: "/chat/$conversationId",
        params: { conversationId: String(conversation.id) },
      });
    } catch (error) {
      throw new Error(resolveChatError(error), { cause: error });
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="flex h-svh min-h-0 flex-col">
      <PageHeader />
      <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-10 overflow-y-auto px-6 py-12">
        <h1 className="text-5xl font-semibold tracking-[0.18em] sm:text-6xl">PUDANS</h1>
        <div className="w-full max-w-3xl space-y-2">
          <ChatComposer
            modes={modes}
            mode={mode}
            onModeChange={setModeOverride}
            agents={agentsQuery.data ?? []}
            agentsLoading={agentsQuery.isPending}
            agent={agent}
            onAgentChange={setAgent}
            models={modelsQuery.data ?? []}
            modelsLoading={modelsQuery.isPending}
            model={model}
            onModelChange={setModel}
            routeGroups={routeGroupsQuery.data ?? []}
            routeGroupsLoading={routeGroupsQuery.isPending}
            routeGroup={routeGroup}
            onRouteGroupChange={setRouteGroup}
            kbCodes={kbCodes}
            knowledgeBases={knowledgeQuery.data ?? []}
            knowledgeLoading={knowledgeQuery.isPending}
            knowledgeError={
              knowledgeQuery.error ? resolveChatError(knowledgeQuery.error) : undefined
            }
            onRetryKnowledge={() => void knowledgeQuery.refetch()}
            onKbCodesChange={setKbCodes}
            commands={commandsQuery.data ?? []}
            commandsLoading={commandsQuery.isFetching}
            onSend={send}
            onGenerate={generate}
            sending={sending}
            placeholder="问点什么，或直接开始一个新会话"
          />
          <p className="text-center text-xs text-muted-foreground">内容由 AI 生成，请仔细甄别</p>
        </div>
      </div>
    </div>
  );
}
