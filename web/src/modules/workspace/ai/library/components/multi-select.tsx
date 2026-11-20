import { useId, useMemo, useState } from "react";

import { ChevronDown, X } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { ScrollArea } from "@/components/ui/scroll-area";
import { cn } from "@/lib/utils";

export type MultiSelectOption = {
  value: string;
  label: string;
  hint?: string;
  disabled?: boolean;
};

type MultiSelectProps = {
  id?: string;
  value: readonly string[];
  onChange: (value: string[]) => void;
  options: readonly MultiSelectOption[];
  placeholder: string;
  emptyMessage: string;
  searchPlaceholder?: string;
  loading?: boolean;
  disabled?: boolean;
  invalid?: boolean;
  className?: string;
};

export function MultiSelect({
  id,
  value,
  onChange,
  options,
  placeholder,
  emptyMessage,
  searchPlaceholder = "搜索",
  loading = false,
  disabled = false,
  invalid = false,
  className,
}: MultiSelectProps) {
  const baseId = useId();
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const optionMap = useMemo(
    () => new Map(options.map((option) => [option.value, option])),
    [options],
  );
  const filteredOptions = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) {
      return options;
    }
    return options.filter(
      (option) =>
        option.label.toLowerCase().includes(normalized) ||
        option.value.toLowerCase().includes(normalized),
    );
  }, [keyword, options]);
  const selectedLabels = value.map((item) => optionMap.get(item)?.label ?? item);

  const toggle = (item: string) => {
    onChange(value.includes(item) ? value.filter((current) => current !== item) : [...value, item]);
  };

  return (
    <div className={cn("space-y-2", className)}>
      <Popover
        open={open}
        onOpenChange={(nextOpen) => {
          setOpen(nextOpen);
          if (!nextOpen) {
            setKeyword("");
          }
        }}
      >
        <PopoverTrigger asChild>
          <Button
            id={id}
            type="button"
            variant="outline"
            aria-expanded={open}
            aria-invalid={invalid || undefined}
            disabled={disabled || loading}
            className="h-8 w-full justify-between font-normal"
          >
            <span className={cn("truncate", value.length === 0 && "text-muted-foreground")}>
              {loading
                ? "加载中…"
                : value.length === 0
                  ? placeholder
                  : value.length === 1
                    ? selectedLabels[0]
                    : `已选择 ${value.length} 项`}
            </span>
            <ChevronDown className="size-4 shrink-0 text-muted-foreground" />
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start" className="w-(--radix-popover-trigger-width) min-w-64 p-0">
          <div className="border-b p-2">
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder={searchPlaceholder}
              className="h-8"
              autoComplete="off"
              spellCheck={false}
            />
          </div>
          <ScrollArea type="auto" className="max-h-60">
            {filteredOptions.length === 0 ? (
              <p className="px-3 py-6 text-center text-xs text-muted-foreground">{emptyMessage}</p>
            ) : (
              <div className="p-1">
                {filteredOptions.map((option, index) => {
                  const optionId = `${baseId}-${index}`;
                  return (
                    <div
                      key={option.value}
                      className={cn(
                        "flex items-start gap-2 rounded-md px-2 py-1.5 text-sm",
                        option.disabled
                          ? "cursor-not-allowed opacity-50"
                          : "focus-within:bg-accent hover:bg-accent",
                      )}
                    >
                      <Checkbox
                        id={optionId}
                        checked={value.includes(option.value)}
                        disabled={option.disabled}
                        onCheckedChange={() => toggle(option.value)}
                        className="mt-0.5"
                      />
                      <label
                        htmlFor={optionId}
                        className={cn(
                          "min-w-0 flex-1",
                          option.disabled ? "cursor-not-allowed" : "cursor-pointer",
                        )}
                      >
                        <span className="block truncate">{option.label}</span>
                        {option.hint ? (
                          <span className="block truncate text-[11px] text-muted-foreground">
                            {option.hint}
                          </span>
                        ) : null}
                      </label>
                    </div>
                  );
                })}
              </div>
            )}
          </ScrollArea>
          {value.length > 0 ? (
            <div className="flex items-center justify-between border-t px-2 py-1.5">
              <span className="text-[11px] text-muted-foreground">已选 {value.length} 项</span>
              <Button type="button" variant="ghost" size="xs" onClick={() => onChange([])}>
                清空
              </Button>
            </div>
          ) : null}
        </PopoverContent>
      </Popover>
      {value.length > 0 ? (
        <div className="flex flex-wrap gap-1">
          {value.map((item) => {
            const label = optionMap.get(item)?.label ?? item;
            return (
              <Badge key={item} variant="secondary" className="gap-1 pe-1">
                <span className="max-w-40 truncate">{label}</span>
                <button
                  type="button"
                  aria-label={`移除 ${label}`}
                  onClick={() => toggle(item)}
                  className="rounded-full p-0.5 hover:bg-background/60"
                >
                  <X className="size-3" />
                </button>
              </Badge>
            );
          })}
        </div>
      ) : null}
    </div>
  );
}
