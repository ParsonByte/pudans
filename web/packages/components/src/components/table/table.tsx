import { type HTMLAttributes, type Key, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "../../utils/cn";
import { EmptyState } from "../empty-state";
import { Skeleton } from "../skeleton";
import styles from "./table.module.css";

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
 * Headless-ish data table with render-slot columns, zebra rows and optional
 * keyboard-accessible row activation.
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
    <div
      className={cn(
        styles.wrapper,
        variant === "plain" && styles.plain,
        "pd-scrollbar",
        wrapperClassName,
      )}
    >
      <table
        {...props}
        aria-busy={loading || undefined}
        className={cn(
          styles.table,
          zebra && styles.zebra,
          hoverable && styles.hoverable,
          stickyHeader && styles.stickyHeader,
          className,
        )}
      >
        {caption ? <caption className={styles.caption}>{caption}</caption> : null}
        <thead className={styles.head}>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cn(styles.th, column.className)}
                style={{
                  width: column.width,
                  textAlign: column.align ?? "left",
                }}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            Array.from({ length: loadingRows }).map((_, rowIndex) => (
              <tr key={"skeleton-" + rowIndex} className={styles.row}>
                {columns.map((column, columnIndex) => (
                  <td key={column.key} className={styles.td}>
                    <Skeleton
                      variant="text"
                      width={columnIndex === 0 ? "60%" : "40%"}
                      height={18}
                    />
                  </td>
                ))}
              </tr>
            ))
          ) : data.length === 0 ? (
            <tr>
              <td className={styles.empty} colSpan={columns.length}>
                {emptyState ?? <EmptyState compact size="sm" title={emptyText} />}
              </td>
            </tr>
          ) : (
            data.map((row, index) => (
              <tr
                key={getRowKey(row, index)}
                className={cn(styles.row, onRowClick && styles.clickable)}
                onClick={onRowClick ? () => onRowClick(row, index) : undefined}
                onKeyDown={onRowClick ? (event) => handleRowKeyDown(event, row, index) : undefined}
                tabIndex={onRowClick ? 0 : undefined}
              >
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={cn(styles.td, column.className)}
                    style={{ textAlign: column.align ?? "left" }}
                  >
                    {column.render
                      ? column.render(row, index)
                      : String((row as Record<string, unknown>)[column.key] ?? "")}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
