import { useMemo, useState } from "react";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { KeyRound, Loader2, MoreHorizontal, RefreshCw, Search } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  ConsoleEmpty,
  ConsoleSection,
  ConsoleSectionHeader,
} from "@/modules/workspace/console/components/console-page";
import { formatUserDateTime } from "@/modules/workspace/console/users/lib/format";

import type { AiModelItem } from "../../providers/lib/api";
import {
  type GatewayCreatedToken,
  type GatewayTokenCreation,
  type GatewayTokenItem,
  deleteGatewayToken,
  gatewayKeySpecConfigured,
  isForbiddenError,
  resolveGatewayApiError,
  restoreGatewayToken,
  revokeGatewayToken,
} from "../lib/api";
import {
  type GatewayTokenState,
  gatewayKeyLimitSummary,
  gatewayLimitPeriodLabel,
  gatewayServiceTierLabel,
  gatewayTokenFingerprint,
  gatewayTokenState,
  gatewayTokenStateLabel,
} from "../lib/model";
import { CopyButton, CopyField } from "./copy-field";
import { GatewayKeyDialog } from "./gateway-key-dialog";

type KeyActionType = "revoke" | "restore" | "delete";

type KeyAction = {
  type: KeyActionType;
  token: GatewayTokenItem;
};

type GatewayKeysProps = {
  tokens: GatewayTokenItem[] | undefined;
  loading: boolean;
  error: unknown;
  canCreate: boolean;
  canManage: boolean;
  models: AiModelItem[];
  advancedSupported: boolean;
  createdToken: GatewayCreatedToken | null;
  onCreated: (result: GatewayTokenCreation) => void;
  onDismissCreated: () => void;
  onRetry: () => void;
  createOpen: boolean;
  onCreateOpenChange: (open: boolean) => void;
};

const STATE_FILTER_OPTIONS: { value: "all" | GatewayTokenState; label: string }[] = [
  { value: "all", label: "全部状态" },
  { value: "ACTIVE", label: "有效" },
  { value: "REVOKED", label: "已吊销" },
  { value: "EXPIRED", label: "已过期" },
];

const ACTION_COPY: Record<
  KeyActionType,
  {
    title: string;
    confirm: string;
    description: (token: GatewayTokenItem) => string;
    toast: string;
  }
> = {
  revoke: {
    title: "吊销网关密钥",
    confirm: "吊销",
    description: (token) =>
      `确定吊销「${token.spec.name || token.metadata.name}」？吊销后使用该密钥的 Agent 将立即无法调用网关，恢复后可继续使用。`,
    toast: "网关密钥已吊销。",
  },
  restore: {
    title: "恢复网关密钥",
    confirm: "恢复",
    description: (token) =>
      `确定恢复「${token.spec.name || token.metadata.name}」？恢复后该密钥立即重新生效。`,
    toast: "网关密钥已恢复。",
  },
  delete: {
    title: "删除网关密钥",
    confirm: "删除",
    description: (token) =>
      `确定永久删除「${token.spec.name || token.metadata.name}」？删除后无法恢复，使用该密钥的 Agent 将立即失效。`,
    toast: "网关密钥已删除。",
  },
};

export function GatewayKeys({
  tokens,
  loading,
  error,
  canCreate,
  canManage,
  models,
  advancedSupported,
  createdToken,
  onCreated,
  onDismissCreated,
  onRetry,
  createOpen,
  onCreateOpenChange,
}: GatewayKeysProps) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [stateFilter, setStateFilter] = useState<"all" | GatewayTokenState>("all");
  const [actionTarget, setActionTarget] = useState<KeyAction | null>(null);
  const items = useMemo(() => tokens ?? [], [tokens]);
  const filtered = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    return items.filter((token) => {
      if (stateFilter !== "all" && gatewayTokenState(token) !== stateFilter) {
        return false;
      }
      if (!normalized) {
        return true;
      }
      return `${token.spec.name} ${token.spec.description ?? ""} ${token.spec.tokenId ?? ""} ${
        token.metadata.name
      }`
        .toLowerCase()
        .includes(normalized);
    });
  }, [items, keyword, stateFilter]);

  const actionMutation = useMutation({
    mutationFn: async (action: KeyAction) => {
      if (action.type === "revoke") {
        await revokeGatewayToken(action.token.metadata.name);
      } else if (action.type === "restore") {
        await restoreGatewayToken(action.token.metadata.name);
      } else {
        await deleteGatewayToken(action.token.metadata.name);
      }
    },
    onSuccess: async (_result, action) => {
      await queryClient.invalidateQueries({ queryKey: ["ai-gateway", "tokens"] });
      setActionTarget(null);
      toast.success(ACTION_COPY[action.type].toast);
    },
    onError: (mutationError) => {
      setActionTarget(null);
      toast.error(resolveGatewayApiError(mutationError));
    },
  });

  const refreshing = loading || actionMutation.isPending;
  const permissionMissing = !canCreate && !canManage;
  const activeAction = actionTarget ? ACTION_COPY[actionTarget.type] : null;

  return (
    <ConsoleSection className="overflow-hidden">
      <ConsoleSectionHeader
        title="网关密钥"
        description="复用个人访问令牌；列表只显示名称、状态与指纹，明文仅在创建时返回一次。"
        action={
          <div className="flex items-center gap-2">
            {canCreate ? (
              <Button type="button" size="sm" onClick={() => onCreateOpenChange(true)}>
                <KeyRound />
                生成密钥
              </Button>
            ) : null}
            {canManage ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={onRetry}
                disabled={refreshing}
              >
                {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
                刷新
              </Button>
            ) : null}
          </div>
        }
      />

      {canManage && items.length > 0 ? (
        <div className="flex flex-wrap items-center gap-2 border-b px-5 py-3">
          <div className="relative">
            <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称、说明或令牌 ID"
              className="h-8 w-72 ps-9"
              aria-label="搜索网关密钥"
            />
          </div>
          <Select
            value={stateFilter}
            onValueChange={(value) => setStateFilter(value as "all" | GatewayTokenState)}
          >
            <SelectTrigger className="h-8 w-32" aria-label="密钥状态">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATE_FILTER_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <span className="text-[11px] text-muted-foreground">
            {filtered.length} / {items.length} 条
          </span>
        </div>
      ) : null}

      {permissionMissing ? (
        <div className="px-5 py-5 text-xs text-muted-foreground">
          当前账号没有网关密钥操作权限，需要管理员授予“生成网关密钥”或“管理网关密钥”操作。
        </div>
      ) : !canManage ? (
        <div className="px-5 py-5 text-xs text-muted-foreground">
          当前账号只有创建权限，无法读取密钥列表。生成密钥后请立即复制并妥善保存明文。
        </div>
      ) : loading ? (
        <div className="space-y-2 px-5 py-4">
          {Array.from({ length: 4 }, (_, index) => (
            <Skeleton key={index} className="h-14 w-full" />
          ))}
        </div>
      ) : error ? (
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-5">
          <div className="space-y-1">
            <p className="text-sm font-medium">无法加载网关密钥</p>
            <p className="text-xs text-muted-foreground">
              {isForbiddenError(error)
                ? "没有读取个人访问令牌的权限，请联系管理员授予“管理网关密钥”操作。"
                : resolveGatewayApiError(error)}
            </p>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            <RefreshCw />
            重试
          </Button>
        </div>
      ) : items.length === 0 ? (
        <ConsoleEmpty>
          <span className="space-y-2">
            <span className="block">还没有网关密钥。</span>
            {canCreate ? (
              <Button type="button" size="sm" onClick={() => onCreateOpenChange(true)}>
                <KeyRound />
                生成第一个密钥
              </Button>
            ) : null}
          </span>
        </ConsoleEmpty>
      ) : filtered.length === 0 ? (
        <ConsoleEmpty>没有匹配的网关密钥，试试调整搜索或状态筛选。</ConsoleEmpty>
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>指纹</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>到期</TableHead>
                <TableHead>最近使用</TableHead>
                <TableHead>权限与限制</TableHead>
                <TableHead className="text-end">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((token) => (
                <TokenRow
                  key={token.metadata.name}
                  token={token}
                  onAction={(type) => setActionTarget({ type, token })}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t px-5 py-3 text-[11px] text-muted-foreground">
        <span>密钥通过 Authorization: Bearer 请求头传递。</span>
        <span>吊销可恢复，删除不可恢复。</span>
        <span>
          {advancedSupported
            ? "密钥级模型、并发和预算限制由后端按令牌解析。"
            : "未检测到可用的密钥级限制配置，创建表单只展示基础字段。"}
        </span>
      </div>

      <GatewayKeyDialog
        open={createOpen}
        onOpenChange={onCreateOpenChange}
        models={models}
        advancedSupported={advancedSupported}
        onCreated={onCreated}
      />

      <ConfirmDialog
        open={actionTarget !== null}
        onOpenChange={(open) => {
          if (!open && !actionMutation.isPending) {
            setActionTarget(null);
          }
        }}
        title={activeAction?.title ?? "操作网关密钥"}
        desc={actionTarget && activeAction ? activeAction.description(actionTarget.token) : ""}
        destructive={actionTarget?.type !== "restore"}
        confirmText={activeAction?.confirm}
        isLoading={actionMutation.isPending}
        handleConfirm={() => {
          if (actionTarget) {
            actionMutation.mutate(actionTarget);
          }
        }}
      />

      <Dialog
        open={createdToken !== null}
        onOpenChange={(open) => {
          if (!open) {
            onDismissCreated();
          }
        }}
      >
        <DialogContent showCloseButton={false} className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>网关密钥已生成</DialogTitle>
            <DialogDescription>
              这是唯一一次显示明文。关闭后页面无法再次读取，请立即复制并妥善保存。
            </DialogDescription>
          </DialogHeader>
          {createdToken ? (
            <div className="space-y-3">
              <div className="grid gap-2 text-sm">
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">名称</span>
                  <span className="truncate">{createdToken.displayName}</span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">资源名</span>
                  <span className="flex min-w-0 items-center gap-1">
                    <span className="truncate font-mono text-xs">{createdToken.name}</span>
                    <CopyButton value={createdToken.name} label="资源名" />
                  </span>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-muted-foreground">到期时间</span>
                  <span>{formatUserDateTime(createdToken.expiresAt)}</span>
                </div>
              </div>
              {createdToken.token ? (
                <CopyField
                  label="密钥明文"
                  value={createdToken.token}
                  hint="用作 Bearer Token；请勿写入代码仓库或公开分享。"
                />
              ) : (
                <p className="border border-warning/40 bg-warning/5 px-3 py-2 text-xs text-warning">
                  后端没有在创建响应中返回一次性明文。如仍需要明文，请删除该密钥后重新创建。
                </p>
              )}
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" onClick={onDismissCreated}>
              我已保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </ConsoleSection>
  );
}

function TokenRow({
  token,
  onAction,
}: {
  token: GatewayTokenItem;
  onAction: (type: KeyActionType) => void;
}) {
  const state = gatewayTokenState(token);
  const gateway = token.spec.gateway;
  const gatewayConfigured = gatewayKeySpecConfigured(gateway);
  const limits = gateway?.limits ?? [];
  const roles = token.spec.roles ?? [];
  const scopes = token.spec.scopes ?? [];

  return (
    <TableRow>
      <TableCell className="max-w-64 whitespace-normal">
        <p className="truncate font-medium">{token.spec.name || token.metadata.name}</p>
        {token.spec.description ? (
          <p className="truncate text-[11px] text-muted-foreground">{token.spec.description}</p>
        ) : null}
        <p className="truncate font-mono text-[11px] text-muted-foreground">
          {token.metadata.name}
        </p>
      </TableCell>
      <TableCell>
        <span className="font-mono text-xs">{gatewayTokenFingerprint(token.spec.tokenId)}</span>
        {token.spec.tokenId ? (
          <p
            className="max-w-32 truncate font-mono text-[10px] text-muted-foreground"
            title={token.spec.tokenId}
          >
            {token.spec.tokenId}
          </p>
        ) : null}
      </TableCell>
      <TableCell>
        <Badge
          variant={
            state === "ACTIVE" ? "secondary" : state === "REVOKED" ? "destructive" : "outline"
          }
        >
          {gatewayTokenStateLabel(state)}
        </Badge>
      </TableCell>
      <TableCell className="text-xs">
        {token.spec.expiresAt ? formatUserDateTime(token.spec.expiresAt) : "永不过期"}
      </TableCell>
      <TableCell className="text-xs">
        {token.spec.lastUsed ? formatUserDateTime(token.spec.lastUsed) : "从未使用"}
      </TableCell>
      <TableCell className="max-w-72 whitespace-normal">
        <div className="flex flex-wrap gap-1">
          {roles.includes("role-template-ai-gateway") ? (
            <Badge variant="outline">AI 网关</Badge>
          ) : null}
          <Badge variant="outline">{token.spec.scopeMode ?? "Intersection"}</Badge>
          {roles.length > 1 ? (
            <Badge variant="outline" title={roles.join(", ")}>
              角色 {roles.length}
            </Badge>
          ) : null}
          {scopes.length > 0 ? (
            <Badge variant="outline" title={scopes.join(", ")}>
              Scope {scopes.length}
            </Badge>
          ) : null}
          {gatewayConfigured ? (
            <>
              {gateway?.enabled === false ? <Badge variant="destructive">密钥停用</Badge> : null}
              {gateway?.models && gateway.models.length > 0 ? (
                <Badge variant="secondary">模型 {gateway.models.length}</Badge>
              ) : null}
              {gateway?.maxConcurrency != null ? (
                <Badge variant="secondary">并发 {gateway.maxConcurrency}</Badge>
              ) : null}
              {gateway?.serviceTier && gateway.serviceTier !== "STANDARD" ? (
                <Badge variant="secondary">
                  层级 {gatewayServiceTierLabel(gateway.serviceTier)}
                </Badge>
              ) : null}
              {limits.length > 0 ? <Badge variant="secondary">限制 {limits.length}</Badge> : null}
            </>
          ) : null}
        </div>
        {gatewayConfigured && limits.length > 0 ? (
          <p className="mt-1 line-clamp-2 text-[11px] text-muted-foreground">
            {limits
              .slice(0, 2)
              .map(
                (limit) =>
                  `${gatewayLimitPeriodLabel(limit.period)} · ${gatewayKeyLimitSummary(limit)}`,
              )
              .join("；")}
            {limits.length > 2 ? `；等 ${limits.length} 条` : ""}
          </p>
        ) : null}
      </TableCell>
      <TableCell className="text-end">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button type="button" variant="ghost" size="icon-sm" aria-label="密钥操作">
              <MoreHorizontal />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>{token.spec.name || token.metadata.name}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            {state === "REVOKED" ? (
              <DropdownMenuItem onSelect={() => onAction("restore")}>恢复</DropdownMenuItem>
            ) : state === "ACTIVE" ? (
              <DropdownMenuItem onSelect={() => onAction("revoke")}>吊销</DropdownMenuItem>
            ) : null}
            <DropdownMenuItem variant="destructive" onSelect={() => onAction("delete")}>
              删除
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </TableCell>
    </TableRow>
  );
}
