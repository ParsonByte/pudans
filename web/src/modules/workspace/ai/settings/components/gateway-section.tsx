import { Controller } from "react-hook-form";

import { FormField } from "@/components/form-field";
import { Input } from "@/components/ui/input";
import { GATEWAY_UPSTREAM_PROTOCOL_OPTIONS } from "@/modules/workspace/ai/gateway/lib/model";
import { MultiSelect } from "@/modules/workspace/ai/library/components/multi-select";

import type { SettingsFormApi } from "../lib/schema";
import { NumberField, SettingsSection, SwitchField } from "./settings-fields";

type GatewaySectionProps = {
  form: SettingsFormApi;
};

export function GatewaySection({ form }: GatewaySectionProps) {
  return (
    <SettingsSection
      title="网关"
      description="限流、重试、超时、上下文注入与上游协议；保存在 AI 设置的 gateway 段。"
    >
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <Controller
          control={form.control}
          name="gateway.injectionEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-gateway-injection"
              label="上下文注入"
              description="允许网关请求携带平台上下文。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="gateway.memoryBridgeEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-gateway-memory-bridge"
              label="记忆桥接"
              description="启用网关与记忆作用域之间的桥接。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <Controller
          control={form.control}
          name="gateway.rateLimitEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-gateway-rate-limit"
              label="限流"
              description="启用网关限流与密钥级预算检查。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <NumberField
          id="ai-setting-gateway-max-retries"
          label="最大重试"
          hint="0 表示不重试。"
          min={0}
          registration={form.register("gateway.maxRetries")}
          error={form.formState.errors.gateway?.maxRetries?.message}
        />
        <NumberField
          id="ai-setting-gateway-retry-backoff"
          label="重试退避"
          hint="单位：毫秒。"
          min={0}
          registration={form.register("gateway.retryBackoffMs")}
          error={form.formState.errors.gateway?.retryBackoffMs?.message}
        />
        <NumberField
          id="ai-setting-gateway-request-timeout"
          label="请求超时"
          hint="单位：秒。"
          min={1}
          registration={form.register("gateway.requestTimeoutSeconds")}
          error={form.formState.errors.gateway?.requestTimeoutSeconds?.message}
        />
        <NumberField
          id="ai-setting-gateway-stream-idle-timeout"
          label="流空闲超时"
          hint="单位：秒。"
          min={1}
          registration={form.register("gateway.streamIdleTimeoutSeconds")}
          error={form.formState.errors.gateway?.streamIdleTimeoutSeconds?.message}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <FormField
          label="重试状态码"
          htmlFor="ai-setting-gateway-retry-statuses"
          hint="用逗号分隔，例如 429, 500, 502；留空表示不按状态码重试。"
          error={form.formState.errors.gateway?.retryOnStatuses?.message}
        >
          <Input
            id="ai-setting-gateway-retry-statuses"
            placeholder="429, 500, 502, 503, 504"
            {...form.register("gateway.retryOnStatuses")}
          />
        </FormField>
        <FormField
          label="上游协议"
          htmlFor="ai-setting-gateway-protocols"
          hint="声明网关接受的上游协议；实际模型仍受供应商与路由组约束。"
          error={form.formState.errors.gateway?.supportedProtocols?.message}
        >
          <Controller
            control={form.control}
            name="gateway.supportedProtocols"
            render={({ field }) => (
              <MultiSelect
                id="ai-setting-gateway-protocols"
                value={field.value}
                onChange={field.onChange}
                options={GATEWAY_UPSTREAM_PROTOCOL_OPTIONS}
                placeholder="选择协议"
                emptyMessage="没有可选项"
                searchPlaceholder="搜索协议"
              />
            )}
          />
        </FormField>
      </div>
    </SettingsSection>
  );
}
