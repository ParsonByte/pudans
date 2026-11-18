import { useState } from "react";

import { format } from "date-fns";
import { zhCN } from "date-fns/locale";
import { Calendar as CalendarIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const EARLIEST_DATE = new Date(1900, 0, 1);
const HOURS = Array.from({ length: 24 }, (_, index) => String(index).padStart(2, "0"));
const MINUTES = Array.from({ length: 60 }, (_, index) => String(index).padStart(2, "0"));

type DateTimePickerProps = {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
};

export function DateTimePicker({
  id,
  value,
  onChange,
  placeholder = "选择日期和时间",
}: DateTimePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = parseDateTime(value);
  const hour = selected ? format(selected, "HH") : "00";
  const minute = selected ? format(selected, "mm") : "00";

  const setDate = (date: Date | undefined) => {
    if (!date) {
      onChange("");
      return;
    }
    const next = new Date(date);
    if (selected) {
      next.setHours(selected.getHours(), selected.getMinutes(), 0, 0);
    } else {
      next.setHours(0, 0, 0, 0);
    }
    onChange(format(next, "yyyy-MM-dd'T'HH:mm"));
  };

  const setTime = (nextHour: string, nextMinute: string) => {
    const next = selected ? new Date(selected) : new Date();
    next.setHours(Number(nextHour), Number(nextMinute), 0, 0);
    onChange(format(next, "yyyy-MM-dd'T'HH:mm"));
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          variant="outline"
          data-empty={!selected}
          className="w-full justify-start text-start font-normal data-[empty=true]:text-muted-foreground"
        >
          {selected ? format(selected, "yyyy-MM-dd HH:mm") : <span>{placeholder}</span>}
          <CalendarIcon className="ms-auto h-4 w-4 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <div className="flex">
          <Calendar
            mode="single"
            captionLayout="dropdown"
            selected={selected}
            onSelect={setDate}
            locale={zhCN}
            weekStartsOn={1}
            disabled={(date: Date) => date > new Date() || date < EARLIEST_DATE}
          />
          <div className="flex w-32 flex-col gap-3 border-s p-3">
            <span className="text-xs font-medium text-muted-foreground">时间</span>
            <div className="space-y-1">
              <span className="text-[11px] text-muted-foreground">小时</span>
              <Select value={hour} onValueChange={(value) => setTime(value, minute)}>
                <SelectTrigger className="h-8 w-full" aria-label="小时">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent side="top">
                  {HOURS.map((item) => (
                    <SelectItem key={item} value={item}>
                      {item}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <span className="text-[11px] text-muted-foreground">分钟</span>
              <Select value={minute} onValueChange={(value) => setTime(hour, value)}>
                <SelectTrigger className="h-8 w-full" aria-label="分钟">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent side="top">
                  {MINUTES.map((item) => (
                    <SelectItem key={item} value={item}>
                      {item}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="mt-auto flex flex-col gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  onChange("");
                  setOpen(false);
                }}
              >
                清除
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => {
                  onChange(format(new Date(), "yyyy-MM-dd'T'HH:mm"));
                  setOpen(false);
                }}
              >
                今天
              </Button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}

function parseDateTime(value: string): Date | undefined {
  if (!value) {
    return undefined;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}
