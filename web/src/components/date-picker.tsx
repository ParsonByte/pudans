import { format } from "date-fns";
import { zhCN } from "date-fns/locale";
import { Calendar as CalendarIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

const EARLIEST_DATE = new Date(1900, 0, 1);

type DatePickerProps = {
  selected: Date | undefined;
  onSelect: (date: Date | undefined) => void;
  placeholder?: string;
};

export function DatePicker({ selected, onSelect, placeholder = "选择日期" }: DatePickerProps) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          data-empty={!selected}
          className="w-60 justify-start text-start font-normal data-[empty=true]:text-muted-foreground"
        >
          {selected ? format(selected, "yyyy年M月d日") : <span>{placeholder}</span>}
          <CalendarIcon className="ms-auto h-4 w-4 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0">
        <Calendar
          mode="single"
          captionLayout="dropdown"
          selected={selected}
          onSelect={onSelect}
          locale={zhCN}
          weekStartsOn={1}
          disabled={(date: Date) => date > new Date() || date < EARLIEST_DATE}
        />
      </PopoverContent>
    </Popover>
  );
}
