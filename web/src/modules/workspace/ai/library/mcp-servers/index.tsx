import { useMemo, useState } from "react";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2, PlugZap, Plus } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAllowedOperations } from "@/modules/workspace/lib/permissions";

import { AgentBadges } from "../components/agent-badges";
import { LibraryDeleteDialog } from "../components/library-delete-dialog";
import { LibraryPage } from "../components/library-page";
import { LibraryTable } from "../components/library-table";
import { ResourceRowActions } from "../components/resource-row-actions";
import { resolveLibraryApiError } from "../lib/api";
import { sortBy } from "../lib/model";
import { McpServerDialog } from "./components/mcp-server-dialog";
import { McpTestDialog } from "./components/mcp-test-dialog";
import {
  MCP_SERVERS_QUERY_KEY,
  type McpServerItem,
  type McpTestResult,
  deleteMcpServer,
  fetchMcpServers,
  testMcpServer,
  updateMcpServer,
} from "./lib/api";
import {
  MCP_HEALTH_LABELS,
  MCP_TRANSPORT_LABELS,
  mcpDisplayName,
  mcpEndpointLabel,
  mcpHealthVariant,
} from "./lib/model";

type TestResultState = {
  serverName: string;
  result: McpTestResult;
};

export function AiMcpServersView() {
  const queryClient = useQueryClient();
  const allowedOperations = useAllowedOperations();
  const canCreate = allowedOperations.has("create");
  const canUpdate = allowedOperations.has("update");
  const canDelete = allowedOperations.has("delete");
  const canToggle = allowedOperations.has("toggle");
  const canTest = allowedOperations.has("test");
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<McpServerItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<McpServerItem | null>(null);
  const [testResult, setTestResult] = useState<TestResultState | null>(null);

  const serversQuery = useQuery({
    queryKey: MCP_SERVERS_QUERY_KEY,
    queryFn: fetchMcpServers,
    retry: false,
  });
  const servers = useMemo(
    () => sortBy(serversQuery.data ?? [], mcpDisplayName),
    [serversQuery.data],
  );

  const invalidate = () => queryClient.invalidateQueries({ queryKey: MCP_SERVERS_QUERY_KEY });

  const toggleMutation = useMutation({
    mutationFn: (server: McpServerItem) =>
      updateMcpServer({
        ...server,
        spec: { ...server.spec, enabled: server.spec.enabled === false },
      }),
    onSuccess: async () => {
      await invalidate();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "MCP 服务器")),
  });

  const deleteMutation = useMutation({
    mutationFn: (server: McpServerItem) => deleteMcpServer(server.metadata.name),
    onSuccess: async () => {
      await invalidate();
      setDeleteTarget(null);
      toast.success("MCP 服务器已删除。");
    },
    onError: (error) => {
      setDeleteTarget(null);
      toast.error(resolveLibraryApiError(error, "MCP 服务器"));
    },
  });

  const testMutation = useMutation({
    mutationFn: (server: McpServerItem) => testMcpServer(server.metadata.name),
    onSuccess: async (result, server) => {
      await invalidate();
      setTestResult({ serverName: mcpDisplayName(server), result });
      if (result.healthy) {
        toast.success(result.message || "MCP 服务器连通性正常。");
      } else {
        toast.error(result.message || "MCP 服务器探测失败。");
      }
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "MCP 服务器")),
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (server: McpServerItem) => {
    setEditing(server);
    setEditorOpen(true);
  };

  return (
    <LibraryPage
      title="MCP 服务器"
      heading="MCP 服务器"
      description="管理 Agent 可调用的 MCP 连接、工具范围与绑定关系。"
      count={servers.length}
      countUnit="个 MCP 服务器"
      refreshing={serversQuery.isFetching}
      onRefresh={() => void serversQuery.refetch()}
      actions={
        canCreate ? (
          <Button type="button" size="sm" onClick={openCreate}>
            <Plus />
            新建 MCP 服务器
          </Button>
        ) : null
      }
    >
      <LibraryTable
        loading={serversQuery.isPending}
        error={serversQuery.error}
        isEmpty={servers.length === 0}
        emptyMessage="还没有 MCP 服务器。新建后即可为 Agent 提供外部工具。"
        errorTitle="无法加载 MCP 服务器"
        resolveError={(error) => resolveLibraryApiError(error, "MCP 服务器")}
        onRetry={() => void serversQuery.refetch()}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>传输</TableHead>
              <TableHead>地址 / 命令</TableHead>
              <TableHead>健康</TableHead>
              <TableHead>工具数</TableHead>
              <TableHead>关联 Agent</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {servers.map((server) => {
              const enabled = server.spec.enabled !== false;
              const health = server.status?.health ?? "UNKNOWN";
              const endpoint = mcpEndpointLabel(server);
              const toggling =
                toggleMutation.isPending &&
                toggleMutation.variables?.metadata.name === server.metadata.name;
              const testing =
                testMutation.isPending &&
                testMutation.variables?.metadata.name === server.metadata.name;
              return (
                <TableRow key={server.metadata.name}>
                  <TableCell>
                    <div className="space-y-0.5">
                      <p className="font-medium">{mcpDisplayName(server)}</p>
                      {server.spec.description ? (
                        <p className="max-w-64 truncate text-[11px] text-muted-foreground">
                          {server.spec.description}
                        </p>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{MCP_TRANSPORT_LABELS[server.spec.transport]}</Badge>
                  </TableCell>
                  <TableCell>
                    <span
                      className="block max-w-64 truncate font-mono text-xs text-muted-foreground"
                      title={endpoint}
                    >
                      {endpoint || "—"}
                    </span>
                  </TableCell>
                  <TableCell>
                    <div className="space-y-0.5">
                      <Badge variant={mcpHealthVariant(health)}>{MCP_HEALTH_LABELS[health]}</Badge>
                      {server.status?.message ? (
                        <p
                          className="max-w-56 truncate text-[11px] text-muted-foreground"
                          title={server.status.message}
                        >
                          {server.status.message}
                        </p>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell>{server.status?.toolCount ?? 0}</TableCell>
                  <TableCell>
                    <AgentBadges agents={server.spec.agents} />
                  </TableCell>
                  <TableCell>
                    {canToggle ? (
                      <div className="flex items-center gap-2">
                        {toggling ? (
                          <Loader2 className="size-4 animate-spin text-muted-foreground" />
                        ) : null}
                        <Switch
                          checked={enabled}
                          disabled={toggling}
                          onCheckedChange={() => toggleMutation.mutate(server)}
                          aria-label={enabled ? "停用 MCP 服务器" : "启用 MCP 服务器"}
                        />
                      </div>
                    ) : (
                      <Badge variant={enabled ? "secondary" : "outline"}>
                        {enabled ? "已启用" : "已停用"}
                      </Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <ResourceRowActions
                      canUpdate={canUpdate}
                      canDelete={canDelete}
                      onEdit={() => openEdit(server)}
                      onDelete={() => setDeleteTarget(server)}
                    >
                      {canTest ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon-sm"
                          aria-label="测试连接"
                          disabled={testing}
                          onClick={() => testMutation.mutate(server)}
                        >
                          {testing ? <Loader2 className="animate-spin" /> : <PlugZap />}
                        </Button>
                      ) : null}
                    </ResourceRowActions>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </LibraryTable>

      {editorOpen ? (
        <McpServerDialog
          server={editing ?? undefined}
          onClose={() => {
            setEditorOpen(false);
            setEditing(null);
          }}
        />
      ) : null}

      <LibraryDeleteDialog
        resourceLabel="MCP 服务器"
        name={deleteTarget ? mcpDisplayName(deleteTarget) : null}
        pending={deleteMutation.isPending}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => {
          if (deleteTarget) {
            deleteMutation.mutate(deleteTarget);
          }
        }}
      />

      {testResult ? (
        <McpTestDialog
          serverName={testResult.serverName}
          result={testResult.result}
          onClose={() => setTestResult(null)}
        />
      ) : null}
    </LibraryPage>
  );
}
