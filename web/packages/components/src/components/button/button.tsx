import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import {
  Box,
  Button as MuiButton,
  CircularProgress,
  type ButtonProps as MuiButtonProps,
} from "@mui/material";
import { styled, type Theme } from "@mui/material/styles";

export type ButtonVariant = "primary" | "secondary" | "outline" | "ghost" | "danger";
export type ButtonSize = "sm" | "md" | "lg";
export type ButtonRadius = "sm" | "md" | "lg" | "full";

/** Legacy legacy DefaultButton contract: MUI Button with text.primary text. */
const StyledButton = styled(MuiButton)(({ theme }) => ({
  minHeight: theme.spacing(4),
  "& .MuiButton-startIcon": {
    marginLeft: 0,
  },
}));

/** Legacy SecondaryButton: text.secondary on action.hover, hover action.focus. */
function variantStyles(variant: ButtonVariant, theme: Theme) {
  const hairline = theme.spacing(0.125);
  switch (variant) {
    case "secondary":
      return {
        color: theme.palette.text.secondary,
        backgroundColor: theme.palette.action.hover,
        "&:hover": { backgroundColor: theme.palette.action.focus },
      };
    case "outline":
      return {
        color: theme.palette.text.primary,
        border: `${hairline} solid ${theme.palette.divider}`,
        "&:hover": {
          backgroundColor: theme.palette.action.hover,
          borderColor: theme.palette.divider,
        },
      };
    default:
      return {};
  }
}

function radiusStyles(radius: ButtonRadius, theme: Theme) {
  switch (radius) {
    case "sm":
      return { borderRadius: theme.spacing(0.5) };
    case "md":
      return { borderRadius: theme.spacing(1) };
    case "full":
      return { borderRadius: theme.spacing(10) };
    default:
      return { borderRadius: theme.shape.borderRadius };
  }
}

const VARIANT_PROPS: Record<ButtonVariant, Pick<MuiButtonProps, "variant" | "color">> = {
  primary: { variant: "contained", color: "primary" },
  secondary: { variant: "text", color: "inherit" },
  outline: { variant: "outlined", color: "primary" },
  ghost: { variant: "text", color: "primary" },
  danger: { variant: "contained", color: "error" },
};

const MUI_SIZE: Record<ButtonSize, "small" | "medium" | "large"> = {
  sm: "small",
  md: "medium",
  lg: "large",
};

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "color" | "size"> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  radius?: ButtonRadius;
  block?: boolean;
  loading?: boolean;
  loadingText?: ReactNode;
  startIcon?: ReactNode;
  endIcon?: ReactNode;
}

/**
 * Action button rebuilt on MUI Button to match the legacy DefaultButton /
 * LoadingButton visual: 14px/500 text, padding 6px 16px, minHeight 32px,
 * 12px radius, disableElevation, text.primary labels for outline/ghost.
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = "primary",
    size = "md",
    radius = "lg",
    block = false,
    loading = false,
    loadingText,
    startIcon,
    endIcon,
    className,
    children,
    disabled,
    type = "button",
    ...props
  },
  ref,
) {
  const loadingIndicator = loadingText ? (
    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 1 }}>
      <CircularProgress size="1em" color="inherit" />
      {loadingText}
    </Box>
  ) : undefined;

  return (
    <StyledButton
      ref={ref}
      type={type}
      disabled={disabled || loading}
      loading={loading}
      loadingPosition="center"
      loadingIndicator={loadingIndicator}
      startIcon={startIcon}
      endIcon={endIcon}
      fullWidth={block}
      className={className}
      {...VARIANT_PROPS[variant]}
      size={MUI_SIZE[size]}
      sx={(theme: Theme) => ({ ...variantStyles(variant, theme), ...radiusStyles(radius, theme) })}
      {...(props as MuiButtonProps)}
    >
      {children}
    </StyledButton>
  );
});

Button.displayName = "Button";
