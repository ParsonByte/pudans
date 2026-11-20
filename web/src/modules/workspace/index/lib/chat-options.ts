import { useQuery } from "@tanstack/react-query";

import { fetchKnowledgeBases } from "../../ai/knowledge/lib/api";
import { AI_AGENTS_QUERY_KEY, agentLabel } from "../../ai/library/lib/agents";
import { type AiAgentItem, fetchAllAgents } from "../../ai/library/lib/api";
import { fetchAllProviderModels, fetchAllRouteGroups } from "../../ai/providers/lib/api";
import { type ChatAgent, fetchChatCommands, toChatModels, toChatRouteGroups } from "./chat-api";

function selectChatAgents(agents: AiAgentItem[]): ChatAgent[] {
  return agents
    .filter((agent) => agent.spec.status !== "INACTIVE")
    .map((agent) => ({
      name: agent.metadata.name,
      displayName: agentLabel(agent),
    }));
}

export function useChatAgentsQuery() {
  return useQuery({
    queryKey: AI_AGENTS_QUERY_KEY,
    queryFn: fetchAllAgents,
    select: selectChatAgents,
    retry: false,
  });
}

export function useChatModelsQuery() {
  return useQuery({
    queryKey: ["ai-provider-models", "all"],
    queryFn: fetchAllProviderModels,
    select: toChatModels,
    retry: false,
  });
}

export function useChatRouteGroupsQuery() {
  return useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    select: toChatRouteGroups,
    retry: false,
  });
}

export function useChatCommandsQuery(agent: string | undefined) {
  return useQuery({
    queryKey: ["chat-commands", agent],
    queryFn: () => fetchChatCommands(agent ?? ""),
    enabled: Boolean(agent),
    retry: false,
  });
}

export function useChatKnowledgeBasesQuery() {
  return useQuery({
    queryKey: ["knowledge-bases"],
    queryFn: fetchKnowledgeBases,
    retry: false,
  });
}
