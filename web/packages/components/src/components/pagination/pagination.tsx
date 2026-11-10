import {
  Pagination as MuiPagination,
  type PaginationProps as MuiPaginationProps,
} from "@mui/material";
import { cn } from "../../utils/cn";
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

/** Brand-colored pagination with total/pageSize helpers. */
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
      onChange={(_event, nextPage) => onPageChange(nextPage)}
      shape="rounded"
      className={cn(styles.root, styles[size], className)}
    />
  );
}
