import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Ban, CheckCircle2, Eye, MoreHorizontal, Pencil, Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { ConfirmDialog } from "@/components/confirm-dialog";
import {
  DataTableFacetedFilter,
  DataTablePagination,
  DataTableToolbar,
  LongText,
} from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatBeijingDateTime } from "@/lib/date";
import {
  ConsoleState,
  ConsoleTableSkeleton,
} from "@/modules/workspace/console/components/console-state";

import {
  type TempRoleBindingRecord,
  approveTempRoleBinding,
  deleteTempRoleBinding,
  fetchAllTempRoleBindings,
  resolveTempRoleBindingApiError,
  revokeTempRoleBinding,
} from "../lib/api";
import {
  TEMP_ROLE_ACTIVE,
  TEMP_ROLE_EXPIRED,
  TEMP_ROLE_PENDING,
  TEMP_ROLE_REVOKED,
  TEMP_ROLE_STATUS_OPTIONS,
  tempRoleBindingRoleNames,
  tempRoleStatusLabel,
  tempRoleStatusVariant,
} from "../lib/binding-model";
import { roleDisplayName } from "../lib/role-model";
import { useRoles } from "../lib/use-roles";
import { TempRoleBindingDetailDialog } from "./temp-role-binding-detail-dialog";
import { TempRoleBindingDialog } from "./temp-role-binding-dialog";

type TempRoleBindingsTabProps = {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
};

type ConfirmAction = "approve" | "revoke" | "delete";

type ConfirmTarget = {
  action: ConfirmAction;
  binding: TempRoleBindingRecord;
};

const STATUS_RANK: Record<string, number> = {
  [TEMP_ROLE_PENDING]: 0,
  [TEMP_ROLE_ACTIVE]: 1,
  [TEMP_ROLE_EXPIRED]: 2,
  [TEMP_ROLE_REVOKED]: 3,
};

export function TempRoleBindingsTab({ canCreate, canUpdate, canDelete }: TempRoleBindingsTabProps) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [statusFilters, setStatusFilters] = useState<string[]>([]);
  const [roleFilters, setRoleFilters] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<TempRoleBindingRecord | null>(null);
  const [detailTarget, setDetailTarget] = useState<TempRoleBindingRecord | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<ConfirmTarget | null>(null);

  const bindingsQuery = useQuery({
    queryKey: ["temp-role-bindings"],
    queryFn: fetchAllTempRoleBindings,
    retry: false,
  });
  const rolesQuery = useRoles();
  const roles = useMemo(() => rolesQuery.data ?? [], [rolesQuery.data]);
  const roleLabels = useMemo(() => {
    const labels = new Map<string, string>();
    for (const role of roles) {
      labels.set(role.metadata.name, roleDisplayName(role));
    }
    return labels;
  }, [roles]);
  const roleLabel = (roleName: string) => roleLabels.get(roleName) ?? roleName;

  const bindings = useMemo(() => {
    return [...(bindingsQuery.data ?? [])]
      .filter((binding) => !binding.metadata.deletionTimestamp)
      .sort((left, right) => {
        const rankDelta =
          (STATUS_RANK[left.spec.status ?? ""] ?? 4) - (STATUS_RANK[right.spec.status ?? ""] ?? 4);
        if (rankDelta !== 0) {
          return rankDelta;
        }
        return (
          new Date(right.metadata.creationTimestamp ?? 0).getTime() -
          new Date(left.metadata.creationTimestamp ?? 0).getTime()
        );
      });
  }, [bindingsQuery.data]);

  const roleFilterOptions = useMemo(() => {
    const labels = new Map(roleLabels);
    for (const binding of bindings) {
      for (const roleName of tempRoleBindingRoleNames(binding.spec.roleNames)) {
        if (!labels.has(roleName)) {
          labels.set(roleName, roleName);
        }
      }
    }
    return [...labels.entries()]
      .map(([value, label]) => ({ value, label }))
      .sort((left, right) => left.label.localeCompare(right.label, "zh-CN"));
  }, [bindings, roleLabels]);

  const normalizedSearch = search.trim().toLowerCase();
  const filteredBindings = useMemo(() => {
    return bindings.filter((binding) => {
      const spec = binding.spec;
      if (statusFilters.length > 0 && !statusFilters.includes(spec.status ?? "")) {
        return false;
      }
      const roleNames = tempRoleBindingRoleNames(spec.roleNames);
      if (roleFilters.length > 0 && !roleNames.some((roleName) => roleFilters.includes(roleName))) {
        return false;
      }
      if (!normalizedSearch) {
        return true;
      }
      const text = [
        binding.metadata.name,
        spec.username ?? "",
        spec.reason ?? "",
        spec.ticketId ?? "",
        spec.requestedBy ?? "",
        spec.approvedBy ?? "",
        ...roleNames,
        ...roleNames.map((roleName) => roleLabels.get(roleName) ?? ""),
      ]
        .join(" ")
        .toLowerCase();
      return text.includes(normalizedSearch);
    });
  }, [bindings, normalizedSearch, roleFilters, roleLabels, statusFilters]);

  const total = filteredBindings.length;
  const pageCount = Math.max(1, Math.ceil(total / size));
  const currentPage = Math.min(page, pageCount);
  const pageRows = filteredBindings.slice((currentPage - 1) * size, currentPage * size);
  const pendingCount = bindings.filter(
    (binding) => binding.spec.status === TEMP_ROLE_PENDING,
  ).length;
  const isFiltered = search.trim().length > 0 || statusFilters.length > 0 || roleFilters.length > 0;
  const forbidden =
    isAxiosError(bindingsQuery.error) && bindingsQuery.error.response?.status === 403;

  const actionMutation = useMutation({
    mutationFn: async ({ action, binding }: ConfirmTarget) => {
      if (action === "approve") {
        return approveTempRoleBinding(binding.metadata.name);
      }
      if (action === "revoke") {
        return revokeTempRoleBinding(binding.metadata.name);
      }
      // 删除前先收回尚未终止的临时授权，避免 Redis 中残留授权
      if (binding.spec.status !== TEMP_ROLE_EXPIRED && binding.spec.status !== TEMP_ROLE_REVOKED) {
        await revokeTempRoleBinding(binding.metadata.name);
      }
      await deleteTempRoleBinding(binding.metadata.name);
      return null;
    },
    onSuccess: async (_result, { action }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["temp-role-bindings"] }),
        queryClient.invalidateQueries({ queryKey: ["user-permissions"] }),
      ]);
      setConfirmTarget(null);
      toast.success(
        action === "approve"
          ? "临时授权已批准。"
          : action === "revoke"
            ? "临时授权已撤销。"
            : "临时授权已删除。",
      );
    },
    onError: (error) => {
      setConfirmTarget(null);
      toast.error(resolveTempRoleBindingApiError(error));
    },
  });

  const resetFilters = () => {
    setSearch("");
    setStatusFilters([]);
    setRoleFilters([]);
    setPage(1);
  };

  return (
    <div className="flex flex-1 flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <h2 className="text-sm font-semibold">临时授权</h2>
            {pendingCount > 0 ? <Badge variant="secondary">{pendingCount} 个待审批</Badge> : null}
          </div>
          <p className="text-xs text-muted-foreground">
            临时角色绑定需要审批后生效，到期后自动收回。
          </p>
        </div>
        {canCreate ? (
          <Button type="button" size="sm" onClick={() => setCreateOpen(true)}>
            <Plus />
            申请临时授权
          </Button>
        ) : null}
      </div>

      <DataTableToolbar
        searchPlaceholder="筛选用户名、角色或工单号..."
        searchValue={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(1);
        }}
        filters={
          <>
            <DataTableFacetedFilter
              title="状态"
              options={TEMP_ROLE_STATUS_OPTIONS}
              selected={statusFilters}
              onSelectedChange={(values) => {
                setStatusFilters(values);
                setPage(1);
              }}
            />
            <DataTableFacetedFilter
              title="角色"
              options={roleFilterOptions}
              selected={roleFilters}
              onSelectedChange={(values) => {
                setRoleFilters(values);
                setPage(1);
              }}
            />
          </>
        }
        isFiltered={isFiltered}
        onReset={resetFilters}
      />

      <div className="overflow-hidden border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="px-4">名称</TableHead>
              <TableHead className="px-4">用户</TableHead>
              <TableHead className="px-4">角色</TableHead>
              <TableHead className="px-4">状态</TableHead>
              <TableHead className="px-4">到期时间</TableHead>
              <TableHead className="px-4 text-end">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {bindingsQuery.isPending ? (
              <ConsoleTableSkeleton columns={6} />
            ) : bindingsQuery.isError ? (
              <TableRow>
                <TableCell colSpan={6}>
                  <ConsoleState
                    variant={forbidden ? "permission" : "error"}
                    title={forbidden ? "没有临时授权查看权限" : "无法加载临时授权"}
                    description={
                      forbidden
                        ? "当前账号缺少查看临时角色绑定的权限，请联系管理员授权。"
                        : resolveTempRoleBindingApiError(bindingsQuery.error)
                    }
                    onRetry={forbidden ? undefined : () => void bindingsQuery.refetch()}
                  />
                </TableCell>
              </TableRow>
            ) : pageRows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6}>
                  <ConsoleState
                    variant="empty"
                    title={isFiltered ? "没有匹配的临时授权。" : "暂无临时授权。"}
                  />
                </TableCell>
              </TableRow>
            ) : (
              pageRows.map((binding) => {
                const spec = binding.spec;
                const status = spec.status;
                const roleNames = tempRoleBindingRoleNames(spec.roleNames);
                const canApprove = canUpdate && status === TEMP_ROLE_PENDING;
                const canRevoke =
                  canUpdate && (status === TEMP_ROLE_PENDING || status === TEMP_ROLE_ACTIVE);
                const canEdit = canUpdate && status === TEMP_ROLE_PENDING;

                return (
                  <TableRow key={binding.metadata.name}>
                    <TableCell className="px-4 py-3">
                      <LongText className="max-w-48 font-mono text-xs">
                        {binding.metadata.name}
                      </LongText>
                    </TableCell>
                    <TableCell className="px-4 py-3 text-sm">{spec.username || "—"}</TableCell>
                    <TableCell className="px-4 py-3">
                      {roleNames.length === 0 ? (
                        <span className="text-sm text-muted-foreground">—</span>
                      ) : (
                        <div className="flex flex-wrap gap-1">
                          {roleNames.map((roleName) => (
                            <Badge key={roleName} variant="secondary">
                              {roleLabel(roleName)}
                            </Badge>
                          ))}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="px-4 py-3">
                      <div className="space-y-1">
                        <Badge variant={tempRoleStatusVariant(status)}>
                          {tempRoleStatusLabel(status)}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="px-4 py-3 text-sm text-muted-foreground">
                      {formatBeijingDateTime(spec.expiresAt)}
                    </TableCell>
                    <TableCell className="px-4 py-3 text-end">
                      <DropdownMenu modal={false}>
                        <DropdownMenuTrigger asChild>
                          <Button
                            type="button"
                            variant="ghost"
                            className="flex size-8 p-0 data-[state=open]:bg-muted"
                            aria-label="临时授权操作"
                          >
                            <MoreHorizontal className="size-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end" className="w-44">
                          <DropdownMenuItem onSelect={() => setDetailTarget(binding)}>
                            <Eye />
                            查看详情
                          </DropdownMenuItem>
                          {canApprove ? (
                            <DropdownMenuItem
                              onSelect={() => setConfirmTarget({ action: "approve", binding })}
                            >
                              <CheckCircle2 />
                              批准授权
                            </DropdownMenuItem>
                          ) : null}
                          {canEdit ? (
                            <DropdownMenuItem onSelect={() => setEditTarget(binding)}>
                              <Pencil />
                              编辑申请
                            </DropdownMenuItem>
                          ) : null}
                          {canRevoke ? (
                            <DropdownMenuItem
                              onSelect={() => setConfirmTarget({ action: "revoke", binding })}
                            >
                              <Ban />
                              撤销授权
                            </DropdownMenuItem>
                          ) : null}
                          {canDelete ? (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem
                                variant="destructive"
                                onSelect={() => setConfirmTarget({ action: "delete", binding })}
                              >
                                <Trash2 />
                                删除记录
                              </DropdownMenuItem>
                            </>
                          ) : null}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>

      <DataTablePagination
        currentPage={currentPage}
        pageCount={pageCount}
        pageSize={size}
        onPageChange={setPage}
        onPageSizeChange={(value) => {
          setSize(value);
          setPage(1);
        }}
        disabled={bindingsQuery.isFetching}
        className="mt-auto"
      />

      {createOpen ? <TempRoleBindingDialog onClose={() => setCreateOpen(false)} /> : null}
      {editTarget ? (
        <TempRoleBindingDialog
          key={editTarget.metadata.name}
          binding={editTarget}
          onClose={() => setEditTarget(null)}
        />
      ) : null}
      <TempRoleBindingDetailDialog
        binding={detailTarget}
        roleLabel={roleLabel}
        onClose={() => setDetailTarget(null)}
      />

      <ConfirmDialog
        open={Boolean(confirmTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmTarget(null);
          }
        }}
        title={
          confirmTarget?.action === "approve"
            ? "批准临时授权"
            : confirmTarget?.action === "revoke"
              ? "撤销临时授权"
              : "删除临时授权"
        }
        desc={
          confirmTarget
            ? confirmDescription(confirmTarget.action, confirmTarget.binding, roleLabel)
            : ""
        }
        confirmText={
          confirmTarget?.action === "approve"
            ? "批准"
            : confirmTarget?.action === "revoke"
              ? "撤销"
              : "删除"
        }
        destructive={confirmTarget?.action !== "approve"}
        isLoading={actionMutation.isPending}
        handleConfirm={() => {
          if (confirmTarget) {
            actionMutation.mutate(confirmTarget);
          }
        }}
      />
    </div>
  );
}

function confirmDescription(
  action: ConfirmAction,
  binding: TempRoleBindingRecord,
  roleLabel: (roleName: string) => string,
): string {
  const spec = binding.spec;
  const roles = tempRoleBindingRoleNames(spec.roleNames)
    .map((roleName) => roleLabel(roleName))
    .join("、");
  const target = `「${spec.username || "—"}」的「${roles || "—"}」`;
  if (action === "approve") {
    return `确定批准${target}临时授权？批准后立即授予角色，到期后自动收回。`;
  }
  if (action === "revoke") {
    return `确定撤销${target}临时授权？撤销后角色立即收回。`;
  }
  return `确定删除${target}临时授权记录？此操作无法恢复。`;
}
