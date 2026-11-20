import { ChevronLeft, ChevronRight, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

type CursorPagerProps = {
  page: number;
  pageSize: number;
  hasNext: boolean;
  itemCount: number;
  loading: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onPageSizeChange: (pageSize: number) => void;
};

export function CursorPager({
  page,
  pageSize,
  hasNext,
  itemCount,
  loading,
  onPrevious,
  onNext,
  onPageSizeChange,
}: CursorPagerProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2 border-t px-3 py-2 text-xs text-muted-foreground">
      <span>
        第 {page} 页 · 本页 {itemCount} 条
      </span>
      <div className="flex items-center gap-2">
        <Select
          value={String(pageSize)}
          onValueChange={(value) => onPageSizeChange(Number(value))}
          disabled={loading}
        >
          <SelectTrigger size="sm" className="w-28" aria-label="每页条数">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {PAGE_SIZE_OPTIONS.map((option) => (
              <SelectItem key={option} value={String(option)}>
                每页 {option} 条
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={loading || page <= 1}
          onClick={onPrevious}
        >
          <ChevronLeft />
          上一页
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={loading || !hasNext}
          onClick={onNext}
        >
          {loading ? <Loader2 className="animate-spin" /> : <ChevronRight />}
          下一页
        </Button>
      </div>
    </div>
  );
}
