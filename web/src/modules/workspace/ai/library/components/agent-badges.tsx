import { Badge } from "@/components/ui/badge";

import { useAgentLabels } from "../lib/agents";

const VISIBLE_AGENTS = 2;

type AgentBadgesProps = {
  agents?: readonly string[];
};

export function AgentBadges({ agents }: AgentBadgesProps) {
  const labels = useAgentLabels();

  if (!agents || agents.length === 0) {
    return <span className="text-xs text-muted-foreground">未绑定</span>;
  }

  const visible = agents.slice(0, VISIBLE_AGENTS);
  return (
    <div className="flex flex-wrap items-center gap-1">
      {visible.map((agent) => (
        <Badge key={agent} variant="secondary">
          {labels.get(agent) ?? agent}
        </Badge>
      ))}
      {agents.length > VISIBLE_AGENTS ? (
        <Badge variant="outline">+{agents.length - VISIBLE_AGENTS}</Badge>
      ) : null}
    </div>
  );
}
