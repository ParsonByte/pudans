import { Controller } from "react-hook-form";

import { FormField } from "@/components/form-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { CHAT_MODE_TIER_OPTIONS } from "@/modules/workspace/ai/chat-modes/lib/model";

import type { AiSettingTier } from "../lib/api";
import type { SettingsFormApi } from "../lib/schema";
import { SettingsSection, SwitchField } from "./settings-fields";

type RoutingSectionProps = {
  form: SettingsFormApi;
};

export function RoutingSection({ form }: RoutingSectionProps) {
  return (
    <SettingsSection
      title="路由"
      description="分类器的启用状态与默认档位；路由组仍负责具体的模型选择。"
    >
      <div className="grid gap-4 lg:grid-cols-2">
        <Controller
          control={form.control}
          name="routing.classifierEnabled"
          render={({ field }) => (
            <SwitchField
              id="ai-setting-routing-classifier"
              label="启用分类器"
              description="按请求复杂度自动挑选候选档位。"
              checked={field.value}
              onCheckedChange={field.onChange}
            />
          )}
        />
        <FormField
          label="分类回退档位"
          htmlFor="ai-setting-routing-fallback-tier"
          hint="分类器不可用或无法判断时使用的档位。"
          error={form.formState.errors.routing?.classifierFallbackTier?.message}
        >
          <Controller
            control={form.control}
            name="routing.classifierFallbackTier"
            render={({ field }) => (
              <Select
                value={field.value}
                onValueChange={(value) => field.onChange(value as AiSettingTier)}
              >
                <SelectTrigger id="ai-setting-routing-fallback-tier" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CHAT_MODE_TIER_OPTIONS.map((option) => (
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
