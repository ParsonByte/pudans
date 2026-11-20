import { useState } from "react";

import { format, parseISO } from "date-fns";
import { zhCN } from "date-fns/locale";
import { Calendar as CalendarIcon } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { BEIJING_TIME_ZONE, serverToday } from "@/lib/date";

import {
  USAGE_ALL_RANGE_HINT,
  USAGE_RANGE_OPTIONS,
  type UsageDateRangeValue,
  type UsageRangeOptionValue,
  type UsageRangePreset,
  usageDateRange,
  validateUsageDateRange,
} from "../lib/model";

type UsageDateRangeFilterProps = {
  value: UsageDateRangeValue;
  onChange: (value: UsageDateRangeValue) => void;
  disabled?: boolean;
  idPrefix?: string;
};

export function UsageDateRangeFilter({
  value,
  onChange,
  disabled = false,
  idPrefix = "usage-range",
}: UsageDateRangeFilterProps) {
  const rangeKey = `${value.from}|${value.to}`;
  const [customDraft, setCustomDraft] = useState(() => ({
    key: rangeKey,
    from: value.from,
    to: value.to,
  }));
  const customFrom = customDraft.key === rangeKey ? customDraft.from : value.from;
  const customTo = customDraft.key === rangeKey ? customDraft.to : value.to;
  const today = serverToday();

  const updateCustomFrom = (from: string) => {
    setCustomDraft({ key: rangeKey, from, to: customTo });
  };

  const updateCustomTo = (to: string) => {
    setCustomDraft({ key: rangeKey, from: customFrom, to });
  };

  const handlePresetChange = (preset: UsageRangeOptionValue) => {
    if (preset === "all") {
      return;
    }
    if (preset === "custom") {
      setCustomDraft({ key: rangeKey, from: value.from, to: value.to });
      onChange({ preset, from: value.from, to: value.to });
      return;
    }
    onChange(usageDateRange(preset as Exclude<UsageRangePreset, "custom">));
  };

  const applyCustomRange = () => {
    const error = validateUsageDateRange(customFrom, customTo);
    if (error) {
      toast.error(error);
      return;
    }
    onChange({ preset: "custom", from: customFrom, to: customTo });
  };

  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="space-y-1.5">
        <Label htmlFor={`${idPrefix}-preset`} className="text-xs text-muted-foreground">
          时间范围
        </Label>
        <Select
          value={value.preset}
          onValueChange={(next) => handlePresetChange(next as UsageRangeOptionValue)}
          disabled={disabled}
        >
          <SelectTrigger id={`${idPrefix}-preset`} className="w-36">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {USAGE_RANGE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      {value.preset === "custom" ? (
        <>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">开始日期</Label>
            <DatePicker
              value={customFrom}
              max={today}
              disabled={disabled}
              onChange={updateCustomFrom}
              ariaLabel="开始日期"
            />
          </div>
          <span className="pb-2 text-xs text-muted-foreground">至</span>
          <div className="space-y-1.5">
            <Label className="text-xs text-muted-foreground">结束日期</Label>
            <DatePicker
              value={customTo}
              max={today}
              disabled={disabled}
              onChange={updateCustomTo}
              ariaLabel="结束日期"
            />
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={applyCustomRange}
            disabled={disabled}
          >
            应用
          </Button>
        </>
      ) : (
        <span className="pb-2 text-xs text-muted-foreground">
          {value.from} 至 {value.to}（按 {BEIJING_TIME_ZONE} 自然日）
        </span>
      )}
      <span className="basis-full text-[11px] text-muted-foreground">{USAGE_ALL_RANGE_HINT}</span>
    </div>
  );
}

type DatePickerProps = {
  value: string;
  max: string;
  disabled: boolean;
  ariaLabel: string;
  onChange: (value: string) => void;
};

function DatePicker({ value, max, disabled, ariaLabel, onChange }: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = value ? parseISO(value) : undefined;
  const upperBound = parseISO(max);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="w-40 justify-start font-normal"
          disabled={disabled}
          aria-label={ariaLabel}
        >
          <CalendarIcon className="size-4 opacity-60" />
          {selected && !Number.isNaN(selected.getTime())
            ? format(selected, "yyyy-MM-dd")
            : "选择日期"}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          captionLayout="dropdown"
          selected={selected}
          onSelect={(date) => {
            if (date) {
              onChange(format(date, "yyyy-MM-dd"));
            }
            setOpen(false);
          }}
          locale={zhCN}
          weekStartsOn={1}
          disabled={(date: Date) => date > upperBound}
        />
      </PopoverContent>
    </Popover>
  );
}
