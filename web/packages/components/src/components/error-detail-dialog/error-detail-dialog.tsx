import type { ReactNode } from "react";
import {
  Box,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Paper,
} from "@mui/material";
import { styled } from "@mui/material/styles";
import { Dialog, StyledDialogContentText } from "../dialog";

export interface ErrorDetail {
  key: string;
  label?: ReactNode;
  message: string;
}

export interface ErrorDetailDialogProps {
  open: boolean;
  onClose: () => void;
  title?: ReactNode;
  message?: ReactNode;
  errors?: readonly ErrorDetail[];
  requestId?: string;
  footer?: ReactNode;
}

/** Legacy StyledTableContainerPaper: 1px divider border, no shadow. */
const StyledTableContainerPaper = styled(Paper)(({ theme }) => ({
  boxShadow: "none",
  border: `1px solid ${theme.palette.divider}`,
}));

/**
 * Legacy AggregatedErrorDetail dialog: DraggableDialog with a body2 message,
 * a small object/error table and a caption request id.
 */
export function ErrorDetailDialog({
  open,
  onClose,
  title = "Error details",
  message,
  errors,
  requestId,
  footer,
}: ErrorDetailDialogProps) {
  const rows = errors ?? [];

  return (
    <Dialog open={open} onClose={onClose} title={title} size="sm" fullWidth footer={footer}>
      <Stack spacing={2}>
          {message ? <StyledDialogContentText>{message}</StyledDialogContentText> : null}
          {rows.length > 0 ? (
            <TableContainer component={StyledTableContainerPaper}>
              <Table size="small" sx={{ width: "100%" }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Object</TableCell>
                    <TableCell>Error</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((error) => (
                    <TableRow key={error.key} hover sx={{ "&:last-child td": { border: 0 } }}>
                      <TableCell component="th" scope="row">
                        <Box sx={{ maxWidth: (theme) => theme.spacing(31.25), overflow: "hidden", textOverflow: "ellipsis" }}>
                          {error.label ?? error.key}
                        </Box>
                      </TableCell>
                      <TableCell>{error.message}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          ) : null}
          {requestId ? (
            <Typography variant="caption" component="div" color="text.secondary">
              Request ID: <Box component="code">{requestId}</Box>
            </Typography>
          ) : null}
      </Stack>
    </Dialog>
  );
}