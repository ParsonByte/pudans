import { useState } from "react";

import { KeyRound, Loader2, Plus } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import type { AiModelItem, AiProviderItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import type { GatewayCreatedToken, GatewayTokenItem } from "../lib/api";
import {
  GATEWAY_EXAMPLES,
  GATEWAY_KEY_PLACEHOLDER,
  GATEWAY_OS_OPTIONS,
  GATEWAY_PROTOCOLS,
  type GatewayExample,
  type GatewayExampleOs,
  type GatewayProtocol,
  defaultGatewayExampleOs,
  gatewayApiBase,
  gatewayExamples,
  gatewayProtocolAgentSource,
  gatewayProtocolBaseHint,
  gatewayProtocolDescription,
  gatewayTokenFingerprint,
  gatewayTokenState,
} from "../lib/model";
import { CopyButton, CopyField } from "./copy-field";

type GatewayAccessProps = {
  models: AiModelItem[];
  providers: AiProviderItem[];
  entryUrl: string | undefined;
  tokens: GatewayTokenItem[];
  tokensLoading: boolean;
  canListTokens: boolean;
  createdToken: GatewayCreatedToken | null;
  canCreateToken: boolean;
  onCreateToken: () => void;
};

type KeyOption = {
  value: string;
  label: string;
  hint: string;
  token: string | undefined;
};

export function GatewayAccess({
  models,
  providers,
  entryUrl,
  tokens,
  tokensLoading,
  canListTokens,
  createdToken,
  canCreateToken,
  onCreateToken,
}: GatewayAccessProps) {
  const [protocol, setProtocol] = useState<GatewayProtocol>("openai");
  const [example, setExample] = useState<GatewayExample>("shell");
  const [os, setOs] = useState<GatewayExampleOs>(defaultGatewayExampleOs);
  const [keyValue, setKeyValue] = useState("");
  const [modelName, setModelName] = useState("");

  const keyOptions: KeyOption[] = [];
  if (createdToken) {
    keyOptions.push({
      value: `created:${createdToken.name}`,
      label: `${createdToken.displayName}（刚创建）`,
      hint: createdToken.expiresAt ? `到期 ${createdToken.expiresAt.slice(0, 10)}` : "刚创建",
      token: createdToken.token,
    });
  }
  for (const token of tokens) {
    if (token.metadata.name === createdToken?.name || gatewayTokenState(token) !== "ACTIVE") {
      continue;
    }
    keyOptions.push({
      value: `token:${token.metadata.name}`,
      label: token.spec.name || token.metadata.name,
      hint: `${gatewayTokenFingerprint(token.spec.tokenId)} · ${
        token.spec.expiresAt ? `到期 ${token.spec.expiresAt.slice(0, 10)}` : "永不过期"
      }`,
      token: undefined,
    });
  }
  const selectedKey = keyOptions.find((option) => option.value === keyValue) ?? keyOptions[0];
  const model = models.find((item) => item.metadata.name === modelName) ?? models[0];
  const provider = model
    ? providers.find((item) => item.metadata.name === model.spec.provider)
    : undefined;
  const modelId = model?.metadata.name ?? "<model>";
  const key = selectedKey?.token ?? GATEWAY_KEY_PLACEHOLDER;
  const base = gatewayApiBase(protocol, entryUrl);
  const examples = gatewayExamples(protocol, modelId, key, os);

  return (
    <section className="rounded-xl border bg-card text-card-foreground">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">接入</h2>
          <p className="text-xs text-muted-foreground">
            把 Agent 的 API 地址指向 PUDANS 网关，模型由路由组选择。
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">
            <KeyRound />
            个人访问令牌
          </Badge>
          {canCreateToken ? (
            <Button type="button" variant="outline" size="sm" onClick={onCreateToken}>
              <Plus />
              生成密钥
            </Button>
          ) : null}
        </div>
      </div>

      <Tabs
        value={protocol}
        onValueChange={(value) => setProtocol(value as GatewayProtocol)}
        className="gap-0"
      >
        <div className="border-b px-5 pt-4">
          <TabsList variant="line" className="h-8">
            {GATEWAY_PROTOCOLS.map((item) => (
              <TabsTrigger key={item.value} value={item.value} className="px-3">
                {item.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>

        {GATEWAY_PROTOCOLS.map((item) => (
          <TabsContent key={item.value} value={item.value} className="mt-0">
            <div className="space-y-5 px-5 py-5">
              <p className="text-xs leading-5 text-muted-foreground">
                {gatewayProtocolDescription(item.value)}
              </p>

              <div className="grid gap-4 lg:grid-cols-2">
                <CopyField
                  label="Base URL"
                  value={base}
                  hint={gatewayProtocolBaseHint(item.value)}
                />

                <div className="space-y-2">
                  <p className="text-xs text-muted-foreground">API 密钥</p>
                  <Select
                    value={selectedKey?.value ?? ""}
                    onValueChange={setKeyValue}
                    disabled={keyOptions.length === 0}
                  >
                    <SelectTrigger className="w-full" aria-label="选择网关密钥">
                      <SelectValue
                        placeholder={tokensLoading ? "密钥加载中…" : "选择已保存的网关密钥"}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      {keyOptions.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>

                  {selectedKey ? (
                    selectedKey.token ? (
                      <CopyField
                        label="一次性明文"
                        value={selectedKey.token}
                        hint="密钥只显示这一次，请立即保存；关闭页面后无法再次读取。"
                      />
                    ) : (
                      <div className="space-y-1.5">
                        <div className="flex items-center gap-2 rounded-lg border bg-muted/30 py-2 ps-3 pe-3">
                          <span className="min-w-0 flex-1 truncate font-mono text-xs text-muted-foreground">
                            {GATEWAY_KEY_PLACEHOLDER}
                          </span>
                          <Badge variant="outline">已隐藏</Badge>
                        </div>
                        <p className="text-[11px] text-muted-foreground">
                          密钥明文只在创建时显示一次；请把示例中的占位符替换为已保存的密钥。
                          {selectedKey.hint ? ` ${selectedKey.hint}。` : ""}
                        </p>
                      </div>
                    )
                  ) : (
                    <div className="flex min-h-[38px] flex-wrap items-center gap-2 rounded-lg border border-dashed bg-muted/20 px-3 py-1.5">
                      <span className="text-xs text-muted-foreground">
                        {canCreateToken ? "还没有可用密钥。" : "没有可用的网关密钥。"}
                      </span>
                      {canCreateToken ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="ms-auto"
                          onClick={onCreateToken}
                        >
                          <Plus />
                          生成密钥
                        </Button>
                      ) : null}
                    </div>
                  )}

                  {!canListTokens ? (
                    <p className="text-[11px] text-muted-foreground">
                      当前账号没有密钥列表读取权限，此处只显示刚创建的密钥。
                    </p>
                  ) : tokensLoading ? (
                    <p className="flex items-center gap-1 text-[11px] text-muted-foreground">
                      <Loader2 className="size-3 animate-spin" />
                      正在读取密钥列表
                    </p>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <div className="space-y-2">
                  <p className="text-xs text-muted-foreground">模型</p>
                  <Select
                    value={model?.metadata.name ?? ""}
                    onValueChange={setModelName}
                    disabled={models.length === 0}
                  >
                    <SelectTrigger className="w-full" aria-label="选择模型">
                      <SelectValue placeholder="没有可用模型" />
                    </SelectTrigger>
                    <SelectContent>
                      {models.map((item) => (
                        <SelectItem key={item.metadata.name} value={item.metadata.name}>
                          {modelDisplayName(item)} · {item.spec.modelCode}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <div className="flex items-center gap-2 rounded-lg border bg-muted/30 py-2 ps-3 pe-1.5">
                    <span className="min-w-0 flex-1 truncate font-mono text-xs">{modelId}</span>
                    <CopyButton value={modelId} label="模型 ID" />
                  </div>
                  <p className="text-[11px] text-muted-foreground">
                    {model
                      ? `${modelDisplayName(model)} · ${
                          provider?.spec.displayName ?? model.spec.provider
                        }。传入模型资源名，实际模型由路由组决定。`
                      : "还没有可用模型，请先在供应商里添加账号和模型。"}
                  </p>
                </div>
                <CopyField
                  label="Agent 来源"
                  value={gatewayProtocolAgentSource(item.value)}
                  hint="可通过 x-agent-source 请求头覆盖，用于区分调用来源。"
                />
              </div>

              <div className="space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs text-muted-foreground">示例</p>
                  <div className="flex flex-wrap items-center gap-2">
                    {example === "shell" || example === "curl" ? (
                      <Select
                        value={os}
                        onValueChange={(value) => setOs(value as GatewayExampleOs)}
                      >
                        <SelectTrigger className="h-7 w-40 text-xs" aria-label="示例操作系统">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          {GATEWAY_OS_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    ) : null}
                    <Tabs
                      value={example}
                      onValueChange={(value) => setExample(value as GatewayExample)}
                    >
                      <TabsList className="h-7 bg-muted/40">
                        {GATEWAY_EXAMPLES.map((entry) => (
                          <TabsTrigger
                            key={entry.value}
                            value={entry.value}
                            className="px-2 text-xs"
                          >
                            {entry.label}
                          </TabsTrigger>
                        ))}
                      </TabsList>
                    </Tabs>
                  </div>
                </div>
                <div className="relative overflow-hidden rounded-xl border bg-muted/25">
                  <div className="absolute end-2 top-2 z-10">
                    <CopyButton value={examples[example]} label="示例" />
                  </div>
                  <pre className="max-h-72 overflow-auto p-4 pe-10 text-[11px] leading-5 whitespace-pre">
                    {examples[example]}
                  </pre>
                </div>
                <p className="text-[11px] text-muted-foreground">
                  {(example === "shell" || example === "curl") && os === "windows"
                    ? "Windows 示例使用 PowerShell 语法。"
                    : ""}
                  把这些写进工具配置，工具即改为经 PUDANS 网关调用，而非直连供应商。
                </p>
              </div>
            </div>
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}
