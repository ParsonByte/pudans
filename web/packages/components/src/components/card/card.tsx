import { forwardRef, type HTMLAttributes } from "react";
import { clsx } from "clsx";
import styles from "./card.module.css";

export type CardPadding = "none" | "sm" | "md" | "lg";
export type CardVariant = "outlined" | "elevated";

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padding?: CardPadding;
  variant?: CardVariant;
  hoverable?: boolean;
}

/** Tokenized surface card with a 1px border and optional hover elevation. */
export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  { padding = "md", variant = "outlined", hoverable = false, className, ...props },
  ref,
) {
  return (
    <div
      ref={ref}
      className={clsx(
        styles.root,
        styles[padding],
        styles[variant],
        hoverable && styles.hoverable,
        className,
      )}
      {...props}
    />
  );
});

Card.displayName = "Card";
