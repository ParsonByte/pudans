import { type ReactNode, useMemo } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { KnowledgeBaseItem } from "@/modules/workspace/ai/knowledge/lib/api";
import { MultiSelect } from "@/modules/workspace/ai/library/components/multi-select";

import type { ChatAgent, ChatModel, ChatRouteGroup } from "../lib/chat-api";
import { chatRouteGroupsForMode } from "../lib/chat-functions";

const AUTO_MODEL = "__auto__";

const DEFAULT_AGENT = "__default_agent__";

const AUTO_ROUTE_GROUP = "__auto__";

type ChatSettingsPopoverProps = {
  mode?: string;
  agent?: string;
  agentLocked: boolean;
  agents: ChatAgent[];
  agentsLoading: boolean;
  onAgentChange?: (agent: string | undefined) => void;
  model?: string;
  models: ChatModel[];
  modelsLoading: boolean;
  onModelChange: (model: string | undefined) => void;
  routeGroup?: string;
  routeGroups: ChatRouteGroup[];
  routeGroupsLoading: boolean;
  onRouteGroupChange: (routeGroup: string | undefined) => void;
  kbCodes: string[];
  knowledgeBases: KnowledgeBaseItem[];
  knowledgeLoading: boolean;
  knowledgeError?: string;
  onRetryKnowledge?: () => void;
  onKbCodesChange: (codes: string[]) => void;
  trigger: ReactNode;
};

export function ChatSettingsPopover({
  mode,
  agent,
  agentLocked,
  agents,
  agentsLoading,
  onAgentChange,
  model,
  models,
  modelsLoading,
  onModelChange,
  routeGroup,
  routeGroups,
  routeGroupsLoading,
  onRouteGroupChange,
  kbCodes,
  knowledgeBases,
  knowledgeLoading,
  knowledgeError,
  onRetryKnowledge,
  onKbCodesChange,
  trigger,
}: ChatSettingsPopoverProps) {
  const allowedRouteGroups = chatRouteGroupsForMode(routeGroups, mode);
  const agentLabel = agents.find((item) => item.name === agent)?.displayName ?? agent;
  const knowledgeOptions = useMemo(
    () =>
      knowledgeBases.map((item) => ({
        value: item.metadata.name,
        label: item.spec.displayName || item.metadata.name,
      })),
    [knowledgeBases],
  );

  return (
    <Popover>
      <PopoverTrigger asChild>{trigger}</PopoverTrigger>
      <PopoverContent align="end" className="w-80">
        <PopoverHeader>
          <PopoverTitle>对话设置</PopoverTitle>
        </PopoverHeader>
        <div className="space-y-3">
          <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">Agent</span>
            {agentLocked ? (
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="outline">{agentLabel || "默认 Agent"}</Badge>
                <span className="text-xs text-muted-foreground">会话创建后不可更改</span>
              </div>
            ) : (
              <Select
                value={agent ?? DEFAULT_AGENT}
                onValueChange={(value) =>
                  onAgentChange?.(value === DEFAULT_AGENT ? undefined : value)
                }
                disabled={agentsLoading}
              >
                <SelectTrigger className="w-full" aria-label="选择 Agent">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={DEFAULT_AGENT}>默认 Agent</SelectItem>
                  {agents.map((item) => (
                    <SelectItem key={item.name} value={item.name}>
                      {item.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </div>

          <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">模型</span>
            <Select
              value={model ?? AUTO_MODEL}
              onValueChange={(value) => onModelChange(value === AUTO_MODEL ? undefined : value)}
              disabled={modelsLoading}
            >
              <SelectTrigger className="w-full" aria-label="选择模型">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={AUTO_MODEL}>默认（自动路由）</SelectItem>
                {models.map((item) => (
                  <SelectItem key={item.name} value={item.name}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {modelsLoading ? (
              <p className="text-xs text-muted-foreground">正在加载模型…</p>
            ) : models.length === 0 ? (
              <p className="text-xs text-muted-foreground">没有可用的对话模型。</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">路由组</span>
            <Select
              value={routeGroup ?? AUTO_ROUTE_GROUP}
              onValueChange={(value) =>
                onRouteGroupChange(value === AUTO_ROUTE_GROUP ? undefined : value)
              }
              disabled={routeGroupsLoading}
            >
              <SelectTrigger className="w-full" aria-label="选择路由组">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={AUTO_ROUTE_GROUP}>跟随模式</SelectItem>
                {allowedRouteGroups.map((item) => (
                  <SelectItem key={item.name} value={item.name}>
                    {item.isDefault ? `${item.displayName}（默认）` : item.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {routeGroupsLoading ? (
              <p className="text-xs text-muted-foreground">正在加载路由组…</p>
            ) : allowedRouteGroups.length === 0 ? (
              <p className="text-xs text-muted-foreground">当前模式没有可用的路由组。</p>
            ) : null}
          </div>

          <div className="space-y-1.5">
            <span className="text-xs font-medium text-muted-foreground">知识库</span>
            {knowledgeError ? (
              <div className="flex items-center justify-between gap-2 bg-destructive/5 px-2 py-1.5 text-xs text-destructive">
                <span className="truncate">{knowledgeError}</span>
                {onRetryKnowledge ? (
                  <Button type="button" variant="ghost" size="xs" onClick={onRetryKnowledge}>
                    重试
                  </Button>
                ) : null}
              </div>
            ) : (
              <MultiSelect
                value={kbCodes}
                onChange={onKbCodesChange}
                options={knowledgeOptions}
                placeholder="使用模式默认"
                emptyMessage="没有可用的知识库。"
                searchPlaceholder="搜索知识库"
                loading={knowledgeLoading}
              />
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
