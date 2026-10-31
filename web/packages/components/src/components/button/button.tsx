import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { cva } from "class-variance-authority";
import { cn } from "../../utils/cn";
import styles from "./button.module.css";

const buttonVariants = cva(styles.root, {
  variants: {
    variant: {
      primary: styles.primary,
      secondary: styles.secondary,
      outline: styles.outline,
      ghost: styles.ghost,
    },
    size: {
      sm: styles.sm,
      md: styles.md,
      lg: styles.lg,
    },
    radius: {
      sm: styles.radiusSm,
      md: styles.radiusMd,
      lg: styles.radiusLg,
      full: styles.radiusFull,
    },
  },
  defaultVariants: {
    variant: "primary",
    size: "md",
    radius: "lg",
  },
});

export type ButtonVariant = "primary" | "secondary" | "outline" | "ghost";
export type ButtonSize = "sm" | "md" | "lg";
export type ButtonRadius = "sm" | "md" | "lg" | "full";

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "color"> {
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
 * Token-driven action button. CVA resolves variant modifiers, CSS Modules keep
 * styles local, and the consuming app can safely append Tailwind utilities
 * through className (merged by tailwind-merge).
 */
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
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
  ) => (
    <button
      ref={ref}
      type={type}
      className={cn(
        buttonVariants({ variant, size, radius }),
        block && styles.block,
        loading && styles.loading,
        className,
      )}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      aria-disabled={loading || undefined}
      data-variant={variant}
      data-size={size}
      data-loading={loading ? "true" : undefined}
      {...props}
    >
      {loading ? (
        <span className={styles.spinner} aria-hidden="true" />
      ) : startIcon ? (
        <span className={styles.icon} aria-hidden="true">
          {startIcon}
        </span>
      ) : null}
      <span className={styles.label}>{loading && loadingText ? loadingText : children}</span>
      {!loading && endIcon ? (
        <span className={styles.icon} aria-hidden="true">
          {endIcon}
        </span>
      ) : null}
    </button>
  ),
);

Button.displayName = "Button";
