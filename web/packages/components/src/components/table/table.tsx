import { type HTMLAttributes, type Key, type KeyboardEvent, type ReactNode } from "react";
import Paper from "@mui/material/Paper";
import MuiTable from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { cn } from "@pudans/utils";
import { EmptyState } from "../empty-state";
import { Skeleton } from "../skeleton";

export interface TableColumn<T> {
  key: string;
  header: ReactNode;
  render?: (row: T, index: number) => ReactNode;
  align?: "left" | "center" | "right";
  width?: number | string;
  className?: string;
}

export interface TableProps<T> extends Omit<HTMLAttributes<HTMLTableElement>, "children"> {
  columns: readonly TableColumn<T>[];
  data: readonly T[];
  rowKey?: (row: T, index: number) => Key;
  /** `card` frames the table on its own surface; `plain` blends it into a parent panel. */
  variant?: "card" | "plain";
  zebra?: boolean;
  hoverable?: boolean;
  stickyHeader?: boolean;
  emptyText?: ReactNode;
  /** Custom empty slot; takes precedence over emptyText. */
  emptyState?: ReactNode;
  /** Renders skeleton rows while data is loading. */
  loading?: boolean;
  loadingRows?: number;
  caption?: ReactNode;
  onRowClick?: (row: T, index: number) => void;
  wrapperClassName?: string;
}

/**
 * Legacy Admin table rebuilt with MUI Table primitives: StyledTableContainerPaper
 * (Paper, 1px divider border, 12px radius, no shadow), Table size="small"
 * (TableCell 6px 16px), TableHead fontWeight 500, row hover action.hover and
 * MUI-default divider cell borders.
 */
export function Table<T>({
  columns,
  data,
  rowKey,
  variant = "card",
  zebra = false,
  hoverable = true,
  stickyHeader = false,
  emptyText = "No records",
  emptyState,
  loading = false,
  loadingRows = 4,
  caption,
  onRowClick,
  className,
  wrapperClassName,
  ...props
}: TableProps<T>) {
  const getRowKey = (row: T, index: number): Key => (rowKey ? rowKey(row, index) : index);

  const handleRowKeyDown = (
    event: KeyboardEvent<HTMLTableRowElement>,
    row: T,
    index: number,
  ): void => {
    if (!onRowClick) {
      return;
    }
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onRowClick(row, index);
    }
  };

  return (
    <TableContainer
      component={Paper}
      className={cn(wrapperClassName)}
      sx={(theme) => ({
        width: "100%",
        overflowX: "auto",
        backgroundColor: variant === "card" ? theme.palette.background.paper : "transparent",
        border: variant === "card" ? `1px solid ${theme.palette.divider}` : 0,
        borderRadius: variant === "card" ? `${theme.shape.borderRadius}px` : 0,
        boxShadow: "none",
      })}
    >
      <MuiTable {...props} size="small" stickyHeader={stickyHeader} sx={{ width: "100%", tableLayout: "fixed" }}>
        {caption ? (
          <Typography
            component="caption"
            variant="body2"
            color="text.secondary"
            sx={{ display: "table-caption", padding: 2, textAlign: "left" }}
          >
            {caption}
          </Typography>
        ) : null}
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell
                key={column.key}
                scope="col"
                align={column.align ?? "left"}
                className={cn(column.className)}
                sx={{ width: column.width }}
              >
                {column.header}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody aria-busy={loading || undefined}>
          {loading ? (
            Array.from({ length: loadingRows }).map((_, rowIndex) => (
              <TableRow key={"skeleton-" + rowIndex} hover={hoverable}>
                {columns.map((column, columnIndex) => (
                  <TableCell key={column.key} align={column.align ?? "left"}>
                    <Skeleton variant="text" width={columnIndex === 0 ? "60%" : "40%"} />
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : data.length === 0 ? (
            <TableRow>
              <TableCell colSpan={columns.length} align="center" sx={{ padding: 4 }}>
                {emptyState ?? <EmptyState compact size="sm" title={emptyText} />}
              </TableCell>
            </TableRow>
          ) : (
            data.map((row, index) => (
              <TableRow
                key={getRowKey(row, index)}
                hover={hoverable}
                onClick={onRowClick ? () => onRowClick(row, index) : undefined}
                onKeyDown={onRowClick ? (event) => handleRowKeyDown(event, row, index) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                sx={{
                  ...(zebra && index % 2 === 1 ? { backgroundColor: "action.hover" } : {}),
                  ...(onRowClick ? { cursor: "pointer", "&:focus-visible": { outline: "none", backgroundColor: "action.selected" } } : {}),
                }}
              >
                {columns.map((column) => (
                  <TableCell key={column.key} align={column.align ?? "left"} className={cn(column.className)}>
                    {column.render
                      ? column.render(row, index)
                      : String((row as Record<string, unknown>)[column.key] ?? "")}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </MuiTable>
    </TableContainer>
  );
}
