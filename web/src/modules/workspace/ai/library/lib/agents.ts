import { useMemo } from "react";

import { useQuery } from "@tanstack/react-query";

import { type AiAgentItem, fetchAllAgents } from "./api";

export const AI_AGENTS_QUERY_KEY = ["ai-agents", "all"] as const;

export function useAgentsQuery() {
  return useQuery({
    queryKey: AI_AGENTS_QUERY_KEY,
    queryFn: fetchAllAgents,
    retry: false,
  });
}

export function agentLabel(agent: AiAgentItem): string {
  return agent.spec.displayName || agent.metadata.name;
}

export function agentHint(agent: AiAgentItem): string | undefined {
  const parts: string[] = [];
  if (agent.spec.status === "INACTIVE") {
    parts.push("已停用");
  }
  if (agent.spec.visibility === "PRIVATE") {
    parts.push("私有");
  }
  if (agent.spec.visibility === "RESTRICTED") {
    parts.push("受限");
  }
  return parts.length > 0 ? parts.join(" · ") : undefined;
}

export function useAgentLabels(): Map<string, string> {
  const query = useAgentsQuery();

  return useMemo(
    () => new Map((query.data ?? []).map((agent) => [agent.metadata.name, agentLabel(agent)])),
    [query.data],
  );
}
