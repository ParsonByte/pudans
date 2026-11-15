import type { ReactNode } from "react";
import {
  Box,
  ListItemText,
  MenuItem,
  Select,
  useMediaQuery,
  useTheme,
  type SelectChangeEvent,
} from "@mui/material";
import { cn } from "@pudans/shared";
import { Pagination } from "../pagination";

export interface TablePaginationProps {
  /** 1-based page number. */
  page: number;
  totalItems: number;
  rowsPerPage: number;
  rowsPerPageOptions?: number[];
  onPageChange: (page: number) => void;
  onRowsPerPageChange?: (rowsPerPage: number) => void;
  /** Label for the rows-per-page select, for example "(n) per page". */
  perPageLabel?: (rowsPerPage: number) => ReactNode;
  className?: string;
}

/**
 * Legacy Admin/Common/TablePagination: a flex row (column on mobile) with the
 * MUI Pagination on the left and the dense filled rows-per-page Select on the
 * right, 8px vertical padding and 8px gap.
 */
export function TablePagination({
  page,
  totalItems,
  rowsPerPage,
  rowsPerPageOptions = [5, 10, 25, 50],
  onPageChange,
  onRowsPerPageChange,
  perPageLabel = (rows) => `${rows} per page`,
  className,
}: TablePaginationProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  const handleRowsPerPageChange = (event: SelectChangeEvent<number>): void => {
    onRowsPerPageChange?.(Number(event.target.value));
  };

  return (
    <Box
      className={cn(className)}
      sx={{
        py: 1,
        display: "flex",
        flexDirection: isMobile ? "column" : "row",
        justifyContent: "space-between",
        alignItems: isMobile ? "flex-start" : "center",
        gap: 1,
      }}
    >
      <Pagination
        size={isMobile ? "sm" : "md"}
        count={Math.max(1, Math.ceil(totalItems / Math.max(1, rowsPerPage)))}
        page={page}
        onPageChange={onPageChange}
      />
      {onRowsPerPageChange ? (
        <Select<number>
          variant="filled"
          size="small"
          value={rowsPerPage}
          onChange={handleRowsPerPageChange}
          sx={{
            minHeight: 39,
            "& .MuiFilledInput-input": {
              display: "flex",
              alignItems: "center",
              paddingTop: "4px",
              paddingBottom: "4px",
            },
          }}
          renderValue={(value) => (
            <ListItemText
              primary={perPageLabel(Number(value))}
              slotProps={{ primary: { variant: "body2" } }}
            />
          )}
        >
          {rowsPerPageOptions.map((option) => (
            <MenuItem key={option} value={option}>
              <ListItemText
                primary={perPageLabel(option)}
                slotProps={{ primary: { variant: "body2" } }}
              />
            </MenuItem>
          ))}
        </Select>
      ) : null}
    </Box>
  );
}
