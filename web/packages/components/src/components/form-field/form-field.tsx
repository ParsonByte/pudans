import { useId, type ReactNode } from "react";
import {
  alpha,
  Box,
  FormControl,
  FormHelperText,
  FormLabel,
  ListItem,
  ListItemText,
  Paper,
  Typography,
} from "@mui/material";

export interface FormFieldProps {
  label?: ReactNode;
  description?: ReactNode;
  error?: ReactNode;
  required?: boolean;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}

/**
 * Label + control + helper/error wrapper rebuilt from the legacy
 * StyledFormControl: body2 / 600 title with a 4px bottom margin (theme.spacing(0.5))
 * and no border, matching Admin settings forms.
 */
export function FormField({
  label,
  description,
  error,
  required = false,
  htmlFor,
  children,
  className,
}: FormFieldProps) {
  const generatedId = useId();
  const descriptionId = "field-description-" + generatedId.replace(/:/g, "");
  const errorId = "field-error-" + generatedId.replace(/:/g, "");

  return (
    <FormControl error={Boolean(error)} required={required} fullWidth className={className}>
      {label ? (
        <FormLabel
          htmlFor={htmlFor}
          sx={{
            display: "block",
            mb: 0.5,
            color: "text.primary",
            fontWeight: 600,
            fontSize: "0.875rem",
            lineHeight: 1.43,
            "&.Mui-focused": { color: "text.primary" },
          }}
        >
          {label}
          {required ? (
            <Box component="span" sx={{ ml: 0.25, color: "error.main" }} aria-hidden="true">
              *
            </Box>
          ) : null}
        </FormLabel>
      ) : null}
      {description ? (
        <Typography
          id={descriptionId}
          variant="caption"
          component="p"
          sx={{ color: "text.secondary", mb: 0.5 }}
        >
          {description}
        </Typography>
      ) : null}
      {children}
      {error ? (
        <FormHelperText id={errorId} error sx={{ mx: 0 }}>
          {error}
        </FormHelperText>
      ) : null}
    </FormControl>
  );
}

export interface FormSectionProps {
  title?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  bordered?: boolean;
  className?: string;
}

/** Grouped settings section = legacy AdminCard (Paper, 1px divider, 12px radius). */
export function FormSection({
  title,
  description,
  actions,
  children,
  bordered = true,
  className,
}: FormSectionProps) {
  return (
    <Paper
      elevation={0}
      className={className}
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        padding: 2,
        ...(bordered
          ? {
              border: (theme) => `${theme.spacing(0.125)} solid ${theme.palette.divider}`,
              borderRadius: (theme) => `${theme.shape.borderRadius}px`,
            }
          : {}),
      }}
    >
      {title || description || actions ? (
        <Box
          sx={{
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "space-between",
            gap: 1.5,
          }}
        >
          <Box sx={{ minWidth: 0 }}>
            {title ? (
              <Typography variant="h6" component="h2">
                {title}
              </Typography>
            ) : null}
            {description ? (
              <Typography variant="body2" sx={{ color: "text.secondary" }}>
                {description}
              </Typography>
            ) : null}
          </Box>
          {actions ? (
            <Box sx={{ display: "flex", flex: "0 0 auto", alignItems: "center", gap: 1 }}>
              {actions}
            </Box>
          ) : null}
        </Box>
      ) : null}
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>{children}</Box>
    </Paper>
  );
}

export interface SettingItemProps {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned control (switch, button, select). */
  control?: ReactNode;
  children?: ReactNode;
  className?: string;
}

/**
 * Title/description/control row rebuilt from the legacy SettingListItem:
 * 1px text.primary(23%) border, 12px radius, subtitle2 title + caption copy.
 */
export function SettingItem({
  title,
  description,
  control,
  children,
  className,
}: SettingItemProps) {
  return (
    <ListItem
      className={className}
      sx={{
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: 2,
        mt: 1,
        py: 0.5,
        px: 2,
        border: (theme) => `${theme.spacing(0.125)} solid ${alpha(theme.palette.text.primary, 0.23)}`,
        borderRadius: (theme) => `${theme.shape.borderRadius}px`,
      }}
    >
      <Box sx={{ minWidth: 0, flex: "1 1 auto" }}>
        <ListItemText
          sx={{ my: 0 }}
          primary={title}
          secondary={description}
          slotProps={{
            primary: { variant: "subtitle2", color: "text.primary" },
            secondary: { variant: "caption", noWrap: true },
          }}
        />
        {children}
      </Box>
      {control ? (
        <Box sx={{ display: "flex", flex: "0 0 auto", alignItems: "center", gap: 1, pt: 0.5 }}>
          {control}
        </Box>
      ) : null}
    </ListItem>
  );
}
