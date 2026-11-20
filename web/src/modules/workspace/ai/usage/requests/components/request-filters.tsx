import { useState } from "react";

import { ChevronDown } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

import { UsageDateRangeFilter } from "../../components/usage-date-range";
import { USAGE_STATUS_OPTIONS, type UsageDateRangeValue } from "../../lib/model";
import { type UsageRequestFilterForm, hasAdvancedRequestFilters } from "../lib/filters";

type ModeOption = {
  value: string;
  label: string;
};

type UsageRequestFiltersProps = {
  form: UsageRequestFilterForm;
  modeOptions: ModeOption[];
  disabled?: boolean;
  isFiltered: boolean;
  onChange: (patch: Partial<UsageRequestFilterForm>) => void;
  onRangeChange: (range: UsageDateRangeValue) => void;
  onSubmit: () => void;
  onReset: () => void;
};

export function UsageRequestFilters({
  form,
  modeOptions,
  disabled = false,
  isFiltered,
  onChange,
  onRangeChange,
  onSubmit,
  onReset,
}: UsageRequestFiltersProps) {
  const [advancedOpen, setAdvancedOpen] = useState(() => hasAdvancedRequestFilters(form));

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <div className="grid gap-4 p-5 sm:grid-cols-2 xl:grid-cols-4">
        <div className="sm:col-span-2">
          <UsageDateRangeFilter
            value={form.range}
            onChange={onRangeChange}
            disabled={disabled}
            idPrefix="usage-request-range"
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="usage-request-keyword" className="text-xs text-muted-foreground">
            关键词
          </Label>
          <Input
            id="usage-request-keyword"
            value={form.q}
            onChange={(event) => onChange({ q: event.target.value })}
            placeholder="请求 ID 或会话键"
            autoComplete="off"
            spellCheck={false}
            disabled={disabled}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="usage-request-status" className="text-xs text-muted-foreground">
            状态
          </Label>
          <Select
            value={form.status || "all"}
            onValueChange={(value) => onChange({ status: value === "all" ? "" : value })}
            disabled={disabled}
          >
            <SelectTrigger id="usage-request-status" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部状态</SelectItem>
              {USAGE_STATUS_OPTIONS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="usage-request-mode" className="text-xs text-muted-foreground">
            模式
          </Label>
          <Select
            value={form.mode || "all"}
            onValueChange={(value) => onChange({ mode: value === "all" ? "" : value })}
            disabled={disabled}
          >
            <SelectTrigger id="usage-request-mode" className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">全部模式</SelectItem>
              {modeOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-3 px-5 pb-5">
        <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
          <CollapsibleTrigger asChild>
            <Button type="button" variant="ghost" size="sm" className="h-7 px-2">
              高级筛选
              <ChevronDown
                className={cn("size-4 transition-transform", advancedOpen && "rotate-180")}
              />
            </Button>
          </CollapsibleTrigger>
          <CollapsibleContent>
            <div className="grid gap-3 pt-2 sm:grid-cols-2 xl:grid-cols-4">
              <FilterInput
                id="usage-request-provider"
                label="供应商"
                value={form.provider}
                onChange={(value) => onChange({ provider: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-model"
                label="模型"
                value={form.model}
                onChange={(value) => onChange({ model: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-account"
                label="账号"
                value={form.account}
                onChange={(value) => onChange({ account: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-agent"
                label="Agent"
                value={form.agent}
                onChange={(value) => onChange({ agent: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-route-group"
                label="路由组"
                value={form.routeGroup}
                onChange={(value) => onChange({ routeGroup: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-purpose"
                label="用途"
                value={form.purpose}
                onChange={(value) => onChange({ purpose: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-via"
                label="入口"
                value={form.via}
                onChange={(value) => onChange({ via: value })}
                disabled={disabled}
              />
              <FilterInput
                id="usage-request-caller-key"
                label="调用密钥"
                value={form.callerKey}
                onChange={(value) => onChange({ callerKey: value })}
                disabled={disabled}
              />
            </div>
          </CollapsibleContent>
        </Collapsible>

        <div className="flex items-center gap-2">
          <Button type="submit" disabled={disabled}>
            查询
          </Button>
          {isFiltered ? (
            <Button type="button" variant="outline" onClick={onReset} disabled={disabled}>
              重置
            </Button>
          ) : null}
        </div>
      </div>
    </form>
  );
}

type FilterInputProps = {
  id: string;
  label: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
};

function FilterInput({ id, label, value, disabled, onChange }: FilterInputProps) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id} className="text-xs text-muted-foreground">
        {label}
      </Label>
      <Input
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="留空表示全部"
        autoComplete="off"
        spellCheck={false}
        disabled={disabled}
      />
    </div>
  );
}
