import { useMemo } from "react";

import { agentHint, agentLabel, useAgentsQuery } from "../lib/agents";
import { MultiSelect, type MultiSelectOption } from "./multi-select";

type AgentMultiSelectProps = {
  id?: string;
  value: readonly string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
  invalid?: boolean;
};

export function AgentMultiSelect({
  id,
  value,
  onChange,
  disabled = false,
  invalid = false,
}: AgentMultiSelectProps) {
  const agentsQuery = useAgentsQuery();
  const options = useMemo<MultiSelectOption[]>(
    () =>
      [...(agentsQuery.data ?? [])]
        .sort((left, right) => agentLabel(left).localeCompare(agentLabel(right), "zh-CN"))
        .map((agent) => ({
          value: agent.metadata.name,
          label: agentLabel(agent),
          hint: agentHint(agent),
        })),
    [agentsQuery.data],
  );

  return (
    <MultiSelect
      id={id}
      value={value}
      onChange={onChange}
      options={options}
      placeholder="选择关联 Agent"
      searchPlaceholder="搜索 Agent"
      emptyMessage={agentsQuery.isError ? "无法加载 Agent 列表。" : "暂无可选的 Agent。"}
      loading={agentsQuery.isPending}
      disabled={disabled}
      invalid={invalid}
    />
  );
}
