import { forwardRef, type HTMLAttributes } from "react";
import Paper from "@mui/material/Paper";
import { cn } from "@pudans/shared";

export type CardPadding = "none" | "sm" | "md" | "lg";
export type CardVariant = "outlined" | "elevated";

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padding?: CardPadding;
  variant?: CardVariant;
  hoverable?: boolean;
}

const PADDING: Record<CardPadding, number> = {
  none: 0,
  sm: 1,
  md: 2,
  lg: 3,
};

/**
 * Legacy surface language (AdminCard/BorderedCard + StyledPaper): Paper with a
 * 1px divider border, theme shape radius (12px), no elevation, background.paper.
 * BorderedCard default padding is 16px (theme.spacing(2)).
 */
export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { padding = "md", variant = "outlined", hoverable = false, className, style, children, ...props },
  ref,
) {
  return (
    <Paper
      ref={ref}
      elevation={variant === "elevated" ? 1 : 0}
      className={cn(className)}
      style={style}
      sx={(theme) => ({
        padding: theme.spacing(PADDING[padding]),
        borderRadius: `${theme.shape.borderRadius}px`,
        backgroundColor: theme.palette.background.paper,
        color: theme.palette.text.primary,
        ...(variant === "outlined" && {
          border: `1px solid ${theme.palette.divider}`,
          boxShadow: "none",
        }),
        ...(hoverable && {
          cursor: "pointer",
          transition: theme.transitions.create(["background-color"], {
            duration: theme.transitions.duration.standard,
          }),
          "&:hover": {
            backgroundColor: theme.palette.action.hover,
          },
        }),
      })}
      {...props}
    >
      {children}
    </Paper>
  );
});

Card.displayName = "Card";
