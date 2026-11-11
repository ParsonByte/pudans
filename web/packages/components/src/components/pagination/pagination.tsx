import {
  Pagination as MuiPagination,
  type PaginationProps as MuiPaginationProps,
} from "@mui/material";
import { cn } from "@pudans/utils";
import styles from "./pagination.module.css";

export type PaginationSize = "sm" | "md";

export interface PaginationProps extends Omit<
  MuiPaginationProps,
  "count" | "page" | "onChange" | "size"
> {
  page: number;
  total?: number;
  pageSize?: number;
  count?: number;
  size?: PaginationSize;
  onPageChange: (page: number) => void;
}

/**
 * MUI Pagination with legacy defaults: body2 page items (32x32 / small 26x26),
 * primary selected fill, action.hover hover and item radius from MUI (rounded
 * shape uses theme.shape.borderRadius). No local item styling remains.
 */
export function Pagination({
  page,
  total,
  pageSize = 20,
  count,
  size = "md",
  onPageChange,
  className,
  ...props
}: PaginationProps) {
  const pageCount = count ?? Math.max(1, Math.ceil((total ?? 0) / Math.max(1, pageSize)));

  return (
    <MuiPagination
      {...props}
      page={page}
      count={pageCount}
      size={size === "sm" ? "small" : "medium"}
      onChange={(_event, nextPage) => onPageChange(nextPage)}
      className={cn(styles.root, className)}
    />
  );
}
