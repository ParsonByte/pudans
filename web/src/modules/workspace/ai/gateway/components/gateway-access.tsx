import { useState } from "react";

import { KeyRound, Loader2, Plus } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";

import type { AiModelItem, AiProviderItem } from "../../providers/lib/api";
import { modelDisplayName } from "../../providers/lib/model";
import type { GatewayToken } from "../lib/api";
import {
  GATEWAY_EXAMPLES,
  GATEWAY_PROTOCOLS,
  type GatewayExample,
  type GatewayProtocol,
  gatewayApiBase,
  gatewayExamples,
  gatewayProtocolDescription,
} from "../lib/model";
import { CopyButton, CopyField } from "./copy-field";

type GatewayAccessProps = {
  models: AiModelItem[];
  providers: AiProviderItem[];
  token: GatewayToken | null;
  creatingToken: boolean;
  onCreateToken: () => void;
};

export function GatewayAccess({
  models,
  providers,
  token,
  creatingToken,
  onCreateToken,
}: GatewayAccessProps) {
  const [protocol, setProtocol] = useState<GatewayProtocol>("openai");
  const [example, setExample] = useState<GatewayExample>("shell");
  const model = models[0];
  const provider = model
    ? providers.find((item) => item.metadata.name === model.spec.provider)
    : undefined;
  const modelId = model ? `${model.spec.provider}/${model.spec.modelCode}` : "<provider/model>";
  const key = token?.token ?? "pat_...";
  const base = gatewayApiBase(protocol);
  const examples = gatewayExamples(protocol, modelId, key);

  return (
    <section className="rounded-xl border bg-card text-card-foreground">
      <div className="flex flex-wrap items-start justify-between gap-3 border-b px-5 py-4">
        <div className="space-y-1">
          <h2 className="text-sm font-semibold">接入</h2>
          <p className="text-xs text-muted-foreground">
            把 Agent 的 API 地址指向 PUDANS 网关，模型由路由组选择。
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="outline">
            <KeyRound />
            个人访问令牌
          </Badge>
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
                  hint={
                    item.value === "openai"
                      ? "OpenAI SDK 会在这个地址后追加 /chat/completions。"
                      : "Anthropic SDK 会在这个地址后追加 /v1/messages。"
                  }
                />

                <div className="space-y-1.5">
                  {token ? (
                    <CopyField
                      label="API 密钥"
                      value={token.token}
                      hint={`到期 ${new Date(token.expiresAt).toLocaleDateString("zh-CN")}；密钥只显示一次，请立即保存。`}
                    />
                  ) : (
                    <>
                      <p className="text-xs text-muted-foreground">API 密钥</p>
                      <div className="flex min-h-[38px] flex-wrap items-center gap-2 rounded-lg border border-dashed bg-muted/20 px-3 py-1.5">
                        <span className="text-xs text-muted-foreground">
                          尚未生成，生成后只显示一次。
                        </span>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="ms-auto"
                          onClick={onCreateToken}
                          disabled={creatingToken}
                        >
                          {creatingToken ? <Loader2 className="animate-spin" /> : <Plus />}
                          生成网关密钥
                        </Button>
                      </div>
                    </>
                  )}
                  <p className="text-[11px] text-muted-foreground">
                    使用个人访问令牌访问网关；当前账号需要 AI Agent 网关角色。
                  </p>
                </div>
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <CopyField
                  label="模型"
                  value={modelId}
                  hint={
                    model
                      ? `${modelDisplayName(model)} · ${provider?.spec.displayName ?? model.spec.provider}。实际模型由路由组决定。`
                      : "还没有可用模型，请先在供应商里添加账号和模型。"
                  }
                />
                <CopyField
                  label="Agent 来源"
                  value={item.value === "anthropic" ? "claude-code" : "openai"}
                  hint="可通过 x-agent-source 请求头覆盖，用于区分调用来源。"
                />
              </div>

              <div className="space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs text-muted-foreground">示例</p>
                  <Tabs
                    value={example}
                    onValueChange={(value) => setExample(value as GatewayExample)}
                  >
                    <TabsList className="h-7 bg-muted/40">
                      {GATEWAY_EXAMPLES.map((entry) => (
                        <TabsTrigger key={entry.value} value={entry.value} className="px-2 text-xs">
                          {entry.label}
                        </TabsTrigger>
                      ))}
                    </TabsList>
                  </Tabs>
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
