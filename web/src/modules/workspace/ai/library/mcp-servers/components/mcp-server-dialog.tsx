import { Controller, type Resolver, useForm, useWatch } from "react-hook-form";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ServerCog } from "lucide-react";
import { toast } from "sonner";
import { z } from "zod";

import { FormDialog, FormDialogSection } from "@/components/form-dialog";
import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";

import { KeyValueEditor } from "../../components/key-value-editor";
import { AgentsField, EnabledField } from "../../components/library-dialog-fields";
import { resolveLibraryApiError } from "../../lib/api";
import {
  entriesToRecord,
  fieldMessage,
  keyValueMessage,
  linesToList,
  listToLines,
  recordToEntries,
  resourceGenerateName,
} from "../../lib/model";
import {
  AI_API_VERSION,
  AI_MCP_SERVER_KIND,
  MCP_SERVERS_QUERY_KEY,
  MCP_TRANSPORTS,
  type McpServerItem,
  type McpServerSpec,
  type McpTransport,
  createMcpServer,
  updateMcpServer,
} from "../lib/api";
import { MCP_TRANSPORT_HINTS, MCP_TRANSPORT_LABELS } from "../lib/model";

const keyValueSchema = z.object({
  key: z.string(),
  value: z.string(),
});

const mcpServerSchema = z
  .object({
    displayName: z.string().trim().min(1, "请输入名称。").max(100, "名称不能超过 100 个字符。"),
    description: z.string().trim().max(500, "描述不能超过 500 个字符。"),
    transport: z.enum(MCP_TRANSPORTS),
    command: z.string().trim().max(500, "启动命令过长。"),
    argsText: z.string(),
    url: z.string().trim().max(1000, "接入地址过长。"),
    headers: z.array(keyValueSchema),
    env: z.array(keyValueSchema),
    toolAllowlistText: z.string(),
    agents: z.array(z.string()),
    enabled: z.boolean(),
  })
  .superRefine((values, context) => {
    if (values.transport === "STDIO") {
      if (!values.command) {
        context.addIssue({
          code: "custom",
          path: ["command"],
          message: "STDIO 传输必须填写启动命令。",
        });
      }
      const envMessage = keyValueMessage(values.env);
      if (envMessage) {
        context.addIssue({ code: "custom", path: ["env"], message: envMessage });
      }
      return;
    }
    if (!values.url) {
      context.addIssue({
        code: "custom",
        path: ["url"],
        message: "HTTP/SSE 传输必须填写接入地址。",
      });
    } else if (!/^https?:\/\//i.test(values.url)) {
      context.addIssue({
        code: "custom",
        path: ["url"],
        message: "接入地址必须以 http:// 或 https:// 开头。",
      });
    }
    const headerMessage = keyValueMessage(values.headers);
    if (headerMessage) {
      context.addIssue({ code: "custom", path: ["headers"], message: headerMessage });
    }
  });

type McpServerFormValues = z.infer<typeof mcpServerSchema>;

type McpServerDialogProps = {
  server?: McpServerItem;
  onClose: () => void;
};

function toFormValues(server: McpServerItem | undefined): McpServerFormValues {
  return {
    displayName: server?.spec.displayName ?? "",
    description: server?.spec.description ?? "",
    transport: server?.spec.transport ?? "STDIO",
    command: server?.spec.command ?? "",
    argsText: listToLines(server?.spec.args),
    url: server?.spec.url ?? "",
    headers: recordToEntries(server?.spec.headers),
    env: recordToEntries(server?.spec.env),
    toolAllowlistText: listToLines(server?.spec.toolAllowlist),
    agents: server?.spec.agents ?? [],
    enabled: server?.spec.enabled !== false,
  };
}

function toSpec(values: McpServerFormValues): McpServerSpec {
  const base: McpServerSpec = {
    displayName: values.displayName.trim(),
    description: values.description.trim() || undefined,
    transport: values.transport,
    toolAllowlist: linesToList(values.toolAllowlistText),
    enabled: values.enabled,
    agents: values.agents,
  };
  if (values.transport === "STDIO") {
    return {
      ...base,
      command: values.command.trim(),
      args: linesToList(values.argsText),
      env: entriesToRecord(values.env),
    };
  }
  return {
    ...base,
    url: values.url.trim(),
    headers: entriesToRecord(values.headers),
  };
}

export function McpServerDialog({ server, onClose }: McpServerDialogProps) {
  const queryClient = useQueryClient();
  const form = useForm<McpServerFormValues>({
    resolver: zodResolver(mcpServerSchema) as Resolver<McpServerFormValues>,
    defaultValues: toFormValues(server),
  });
  const transport = useWatch({ control: form.control, name: "transport" });

  const saveMutation = useMutation({
    mutationFn: (values: McpServerFormValues) => {
      const spec = toSpec(values);
      if (server) {
        return updateMcpServer({ ...server, spec });
      }
      return createMcpServer({
        apiVersion: AI_API_VERSION,
        kind: AI_MCP_SERVER_KIND,
        metadata: { generateName: resourceGenerateName(values.displayName, "mcp") },
        spec,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: MCP_SERVERS_QUERY_KEY });
      toast.success(server ? "MCP 服务器已保存。" : "MCP 服务器已创建。");
      onClose();
    },
    onError: (error) => toast.error(resolveLibraryApiError(error, "MCP 服务器")),
  });

  const onSubmit = form.handleSubmit(
    (values) => saveMutation.mutate(values),
    () => toast.error("请检查填写内容。"),
  );

  return (
    <FormDialog
      open
      onClose={onClose}
      icon={<ServerCog className="size-5" />}
      title={server ? `编辑 ${server.spec.displayName || server.metadata.name}` : "新建 MCP 服务器"}
      description="配置传输方式、连接参数与可用的工具范围。"
      form={form}
      onSubmit={onSubmit}
      isPending={saveMutation.isPending}
      contentClassName="sm:max-w-2xl"
      submitLabel={server ? "保存更改" : "创建 MCP 服务器"}
    >
      <FormDialogSection title="基本信息">
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField
            label="名称"
            htmlFor="mcp-display-name"
            error={form.formState.errors.displayName?.message}
          >
            <Input
              id="mcp-display-name"
              placeholder="例如：代码检索"
              {...form.register("displayName")}
            />
          </FormField>
          <FormField
            label="传输方式"
            htmlFor="mcp-transport"
            hint={MCP_TRANSPORT_HINTS[transport]}
            error={form.formState.errors.transport?.message}
          >
            <Controller
              control={form.control}
              name="transport"
              render={({ field }) => (
                <Select
                  value={field.value}
                  onValueChange={(value) => field.onChange(value as McpTransport)}
                >
                  <SelectTrigger id="mcp-transport" className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {MCP_TRANSPORTS.map((item) => (
                      <SelectItem key={item} value={item}>
                        {MCP_TRANSPORT_LABELS[item]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </FormField>
        </div>
        <FormField
          label="描述"
          htmlFor="mcp-description"
          error={form.formState.errors.description?.message}
        >
          <Textarea
            id="mcp-description"
            rows={2}
            placeholder="说明该 MCP 服务器提供的工具与用途。"
            {...form.register("description")}
          />
        </FormField>
      </FormDialogSection>

      <FormDialogSection title="连接参数">
        {transport === "STDIO" ? (
          <>
            <FormField
              label="启动命令"
              htmlFor="mcp-command"
              hint="STDIO 传输通过本地命令启动 MCP 进程。"
              error={form.formState.errors.command?.message}
            >
              <Input
                id="mcp-command"
                placeholder="npx"
                className="font-mono"
                {...form.register("command")}
              />
            </FormField>
            <FormField label="命令参数" htmlFor="mcp-args" hint="每行一个参数；留空表示无参数。">
              <Textarea
                id="mcp-args"
                rows={4}
                placeholder={"-y\n@modelcontextprotocol/server-filesystem\n/workspace"}
                className="font-mono"
                {...form.register("argsText")}
              />
            </FormField>
            <FormField
              label="环境变量"
              htmlFor="mcp-env"
              hint="传递给 MCP 进程的环境变量。"
              error={fieldMessage(form.formState.errors.env)}
            >
              <Controller
                control={form.control}
                name="env"
                render={({ field }) => (
                  <KeyValueEditor
                    value={field.value}
                    onChange={field.onChange}
                    keyPlaceholder="变量名"
                    valuePlaceholder="变量值"
                    addLabel="添加环境变量"
                    disabled={saveMutation.isPending}
                  />
                )}
              />
            </FormField>
          </>
        ) : (
          <>
            <FormField
              label="接入地址"
              htmlFor="mcp-url"
              hint="远程 MCP 服务的 HTTP(S) 地址。"
              error={form.formState.errors.url?.message}
            >
              <Input
                id="mcp-url"
                placeholder="https://mcp.example.com/mcp"
                className="font-mono"
                {...form.register("url")}
              />
            </FormField>
            <FormField
              label="请求头"
              htmlFor="mcp-headers"
              hint="发送到 MCP 服务的附加请求头，例如鉴权信息。"
              error={fieldMessage(form.formState.errors.headers)}
            >
              <Controller
                control={form.control}
                name="headers"
                render={({ field }) => (
                  <KeyValueEditor
                    value={field.value}
                    onChange={field.onChange}
                    keyPlaceholder="Header 名称"
                    valuePlaceholder="Header 值"
                    addLabel="添加请求头"
                    disabled={saveMutation.isPending}
                  />
                )}
              />
            </FormField>
          </>
        )}
      </FormDialogSection>

      <FormDialogSection title="工具与绑定">
        <FormField
          label="工具允许清单"
          htmlFor="mcp-allowlist"
          hint="每行一个工具名；留空表示允许该服务器提供的全部工具。"
        >
          <Textarea
            id="mcp-allowlist"
            rows={4}
            placeholder={"read_file\nlist_directory"}
            className="font-mono"
            {...form.register("toolAllowlistText")}
          />
        </FormField>
        <Controller
          control={form.control}
          name="agents"
          render={({ field }) => <AgentsField value={field.value} onChange={field.onChange} />}
        />
        <Controller
          control={form.control}
          name="enabled"
          render={({ field }) => <EnabledField checked={field.value} onChange={field.onChange} />}
        />
      </FormDialogSection>
    </FormDialog>
  );
}
