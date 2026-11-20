import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

const PAGE_SIZES = [10, 20, 30, 40, 50];

type UsageCursorPaginationProps = {
  page: number;
  pageSize: number;
  hasPrevious: boolean;
  hasNext: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onPageSizeChange: (size: number) => void;
  disabled?: boolean;
  className?: string;
};

export function UsageCursorPagination({
  page,
  pageSize,
  hasPrevious,
  hasNext,
  onPrevious,
  onNext,
  onPageSizeChange,
  disabled = false,
  className,
}: UsageCursorPaginationProps) {
  return (
    <div
      className={cn(
        "flex flex-col gap-3 px-2 sm:flex-row sm:items-center sm:justify-between",
        className,
      )}
    >
      <div className="flex items-center gap-2">
        <Select
          value={String(pageSize)}
          onValueChange={(value) => onPageSizeChange(Number(value))}
          disabled={disabled}
        >
          <SelectTrigger className="h-8 w-20" aria-label="每页显示数量">
            <SelectValue />
          </SelectTrigger>
          <SelectContent side="top">
            {PAGE_SIZES.map((size) => (
              <SelectItem key={size} value={String(size)}>
                {size}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-sm font-medium whitespace-nowrap">每页条数</p>
      </div>
      <div className="flex flex-wrap items-center gap-3 sm:justify-end">
        <p className="text-sm font-medium whitespace-nowrap">第 {page} 页</p>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onPrevious}
            disabled={disabled || !hasPrevious}
          >
            <ChevronLeft />
            上一页
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onNext}
            disabled={disabled || !hasNext}
          >
            下一页
            <ChevronRight />
          </Button>
        </div>
      </div>
    </div>
  );
}
