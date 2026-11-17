import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

type DataTablePaginationProps = {
  currentPage: number;
  pageCount: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  disabled?: boolean;
  className?: string;
};

const PAGE_SIZES = [10, 20, 30, 40, 50];

export function DataTablePagination({
  currentPage,
  pageCount,
  pageSize,
  onPageChange,
  onPageSizeChange,
  disabled = false,
  className,
}: DataTablePaginationProps) {
  const pageNumbers = getPageNumbers(currentPage, pageCount);

  return (
    <div
      className={cn(
        "flex items-center justify-between overflow-clip px-2",
        "@max-2xl/content:flex-col-reverse @max-2xl/content:gap-4",
        className,
      )}
      style={{ overflowClipMargin: 1 }}
    >
      <div className="flex w-full items-center justify-between">
        <div className="flex w-24 items-center justify-center text-sm font-medium @2xl/content:hidden">
          第 {currentPage} / {pageCount} 页
        </div>
        <div className="flex items-center gap-2 @max-2xl/content:flex-row-reverse">
          <Select
            value={String(pageSize)}
            onValueChange={(value) => onPageSizeChange(Number(value))}
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
          <p className="hidden text-sm font-medium sm:block">每页条数</p>
        </div>
      </div>

      <div className="flex items-center sm:space-x-6 lg:space-x-8">
        <div className="flex w-24 items-center justify-center text-sm font-medium @max-3xl/content:hidden">
          第 {currentPage} / {pageCount} 页
        </div>
        <div className="flex items-center space-x-2">
          <Button
            type="button"
            variant="outline"
            className="size-8 p-0 @max-md/content:hidden"
            onClick={() => onPageChange(1)}
            disabled={disabled || currentPage <= 1}
          >
            <span className="sr-only">第一页</span>
            <ChevronsLeft className="size-4" />
          </Button>
          <Button
            type="button"
            variant="outline"
            className="size-8 p-0"
            onClick={() => onPageChange(Math.max(1, currentPage - 1))}
            disabled={disabled || currentPage <= 1}
          >
            <span className="sr-only">上一页</span>
            <ChevronLeft className="size-4" />
          </Button>

          {pageNumbers.map((pageNumber, index) => (
            <div key={`${pageNumber}-${index}`} className="flex items-center">
              {pageNumber === "ellipsis" ? (
                <span className="px-1 text-sm text-muted-foreground">...</span>
              ) : (
                <Button
                  type="button"
                  variant={currentPage === pageNumber ? "default" : "outline"}
                  className="h-8 min-w-8 px-2"
                  onClick={() => onPageChange(pageNumber)}
                  disabled={disabled}
                >
                  <span className="sr-only">第 {pageNumber} 页</span>
                  {pageNumber}
                </Button>
              )}
            </div>
          ))}

          <Button
            type="button"
            variant="outline"
            className="size-8 p-0"
            onClick={() => onPageChange(Math.min(pageCount, currentPage + 1))}
            disabled={disabled || currentPage >= pageCount}
          >
            <span className="sr-only">下一页</span>
            <ChevronRight className="size-4" />
          </Button>
          <Button
            type="button"
            variant="outline"
            className="size-8 p-0 @max-md/content:hidden"
            onClick={() => onPageChange(pageCount)}
            disabled={disabled || currentPage >= pageCount}
          >
            <span className="sr-only">最后一页</span>
            <ChevronsRight className="size-4" />
          </Button>
        </div>
      </div>
    </div>
  );
}

function getPageNumbers(current: number, total: number): (number | "ellipsis")[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index + 1);
  }
  const pages = new Set<number>([1, total, current - 1, current, current + 1]);
  const sorted = [...pages].filter((value) => value >= 1 && value <= total).sort((a, b) => a - b);
  const result: (number | "ellipsis")[] = [];
  let previous = 0;
  for (const value of sorted) {
    if (previous > 0 && value - previous > 1) {
      result.push("ellipsis");
    }
    result.push(value);
    previous = value;
  }
  return result;
}
