import { Controller, useWatch } from "react-hook-form";

import { useQuery } from "@tanstack/react-query";

import { FormField } from "@/components/form-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CHAT_MODES_QUERY_KEY, fetchChatModes } from "@/modules/workspace/ai/chat-modes/lib/api";
import {
  chatModeLabel,
  knowledgeBaseOptions,
  routeGroupOptions,
  withCurrentOption,
} from "@/modules/workspace/ai/chat-modes/lib/model";
import { fetchKnowledgeBases } from "@/modules/workspace/ai/knowledge/lib/api";
import { MultiSelect } from "@/modules/workspace/ai/library/components/multi-select";
import { agentLabel, useAgentsQuery } from "@/modules/workspace/ai/library/lib/agents";
import { fetchAllRouteGroups } from "@/modules/workspace/ai/providers/lib/api";
import { AI_TEAMS_QUERY_KEY, fetchAiTeams } from "@/modules/workspace/ai/teams/lib/api";
import { teamDisplayName } from "@/modules/workspace/ai/teams/lib/model";

import type { SettingsFormApi } from "../lib/schema";
import { NO_SELECTION_VALUE, SettingsSection } from "./settings-fields";

type DefaultsSectionProps = {
  form: SettingsFormApi;
};

export function DefaultsSection({ form }: DefaultsSectionProps) {
  const teamsQuery = useQuery({
    queryKey: AI_TEAMS_QUERY_KEY,
    queryFn: fetchAiTeams,
    retry: false,
  });
  const agentsQuery = useAgentsQuery();
  const routeGroupsQuery = useQuery({
    queryKey: ["ai-route-groups", "all"],
    queryFn: fetchAllRouteGroups,
    retry: false,
  });
  const chatModesQuery = useQuery({
    queryKey: CHAT_MODES_QUERY_KEY,
    queryFn: fetchChatModes,
    retry: false,
  });
  const knowledgeBasesQuery = useQuery({
    queryKey: ["knowledge-bases"],
    queryFn: fetchKnowledgeBases,
    retry: false,
  });

  const defaultTeam = useWatch({ control: form.control, name: "defaults.team" });
  const defaultAgent = useWatch({ control: form.control, name: "defaults.agent" });
  const defaultRouteGroup = useWatch({ control: form.control, name: "defaultRouteGroup" });
  const defaultChatMode = useWatch({ control: form.control, name: "defaultChatMode" });

  const teamOptions = withCurrentOption(
    (teamsQuery.data ?? []).map((team) => ({
      value: team.metadata.name,
      label: teamDisplayName(team),
      hint: team.metadata.name,
    })),
    defaultTeam,
  );
  const agentOptions = withCurrentOption(
    (agentsQuery.data ?? []).map((agent) => ({
      value: agent.metadata.name,
      label: agentLabel(agent),
      hint: agent.metadata.name,
    })),
    defaultAgent,
  );
  const groupOptions = routeGroupOptions(routeGroupsQuery.data ?? [], defaultRouteGroup);
  const modeOptions = withCurrentOption(
    (chatModesQuery.data ?? []).map((mode) => ({
      value: mode.metadata.name,
      label: chatModeLabel(mode),
      hint: mode.metadata.name,
    })),
    defaultChatMode,
  );
  const knowledgeBases = knowledgeBasesQuery.data ?? [];

  return (
    <SettingsSection
      title="默认项"
      description="当 Agent 或对话请求没有显式指定时使用：默认路由组、对话模式、知识库，以及默认团队与 Agent。"
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="默认路由组"
          htmlFor="ai-setting-default-route-group"
          hint={
            routeGroupsQuery.isError
              ? "无法加载路由组，请检查权限后重试。"
              : routeGroupsQuery.isPending
                ? "正在加载路由组…"
                : "留空时由服务端按内置规则选择。"
          }
          error={form.formState.errors.defaultRouteGroup?.message}
        >
          <Controller
            control={form.control}
            name="defaultRouteGroup"
            render={({ field }) => (
              <Select
                value={field.value || NO_SELECTION_VALUE}
                disabled={routeGroupsQuery.isPending}
                onValueChange={(value) => field.onChange(value === NO_SELECTION_VALUE ? "" : value)}
              >
                <SelectTrigger id="ai-setting-default-route-group" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SELECTION_VALUE}>不指定</SelectItem>
                  {groupOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
        <FormField
          label="默认对话模式"
          htmlFor="ai-setting-default-chat-mode"
          hint={
            chatModesQuery.isError
              ? "无法加载对话模式，请检查权限后重试。"
              : chatModesQuery.isPending
                ? "正在加载对话模式…"
                : "用户未选择模式时使用。"
          }
          error={form.formState.errors.defaultChatMode?.message}
        >
          <Controller
            control={form.control}
            name="defaultChatMode"
            render={({ field }) => (
              <Select
                value={field.value || NO_SELECTION_VALUE}
                disabled={chatModesQuery.isPending}
                onValueChange={(value) => field.onChange(value === NO_SELECTION_VALUE ? "" : value)}
              >
                <SelectTrigger id="ai-setting-default-chat-mode" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SELECTION_VALUE}>不指定</SelectItem>
                  {modeOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
      </div>

      <FormField
        label="默认知识库"
        htmlFor="ai-setting-default-knowledge-bases"
        hint={
          knowledgeBasesQuery.isError
            ? "无法加载知识库列表，请检查权限后重试。"
            : "未绑定知识库的请求默认检索这些知识库。"
        }
        error={form.formState.errors.defaultKnowledgeBaseCodes?.message}
      >
        <Controller
          control={form.control}
          name="defaultKnowledgeBaseCodes"
          render={({ field }) => (
            <MultiSelect
              id="ai-setting-default-knowledge-bases"
              value={field.value}
              onChange={field.onChange}
              options={knowledgeBaseOptions(knowledgeBases, field.value)}
              placeholder="选择知识库"
              emptyMessage="没有可选知识库"
              searchPlaceholder="搜索知识库"
              loading={knowledgeBasesQuery.isPending}
            />
          )}
        />
      </FormField>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="默认团队"
          htmlFor="ai-setting-default-team"
          hint={
            teamsQuery.isError
              ? "无法加载团队列表，请检查权限后重试。"
              : "服务端在未指定团队时使用该团队作用域。"
          }
          error={form.formState.errors.defaults?.team?.message}
        >
          <Controller
            control={form.control}
            name="defaults.team"
            render={({ field }) => (
              <Select
                value={field.value || NO_SELECTION_VALUE}
                disabled={teamsQuery.isPending}
                onValueChange={(value) => field.onChange(value === NO_SELECTION_VALUE ? "" : value)}
              >
                <SelectTrigger id="ai-setting-default-team" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SELECTION_VALUE}>不指定</SelectItem>
                  {teamOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
        <FormField
          label="默认 Agent"
          htmlFor="ai-setting-default-agent"
          hint={
            agentsQuery.isError
              ? "无法加载 Agent 列表，请检查权限后重试。"
              : "服务端在未指定 Agent 时使用该默认值。"
          }
          error={form.formState.errors.defaults?.agent?.message}
        >
          <Controller
            control={form.control}
            name="defaults.agent"
            render={({ field }) => (
              <Select
                value={field.value || NO_SELECTION_VALUE}
                disabled={agentsQuery.isPending}
                onValueChange={(value) => field.onChange(value === NO_SELECTION_VALUE ? "" : value)}
              >
                <SelectTrigger id="ai-setting-default-agent" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NO_SELECTION_VALUE}>不指定</SelectItem>
                  {agentOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
        </FormField>
      </div>
    </SettingsSection>
  );
}
