import type { ReactNode } from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./progress-steps.module.css";

export type ProgressStepStatus = "pending" | "active" | "complete" | "error";

export interface ProgressStep {
  key: string;
  label: ReactNode;
  description?: ReactNode;
  status?: ProgressStepStatus;
}

export interface ProgressStepsProps {
  steps: readonly ProgressStep[];
  /** Index of the active step; ignored when each step provides status. */
  current?: number;
  orientation?: "horizontal" | "vertical";
  size?: "sm" | "md";
  className?: string;
}

function resolveStatus(step: ProgressStep, index: number, current?: number): ProgressStepStatus {
  if (step.status) {
    return step.status;
  }
  if (current === undefined) {
    return "pending";
  }
  if (index < current) {
    return "complete";
  }
  return index === current ? "active" : "pending";
}

/**
 * Step progress migrated from the legacy Tasks StepProgressBar. Used by
 * onboarding, upload and multi-stage dialogs.
 */
export function ProgressSteps({
  steps,
  current,
  orientation = "horizontal",
  size = "md",
  className,
}: ProgressStepsProps) {
  return (
    <ol
      className={cn(styles.root, styles[orientation], styles[size], className)}
      aria-label="Progress"
    >
      {steps.map((step, index) => {
        const status = resolveStatus(step, index, current);
        return (
          <li
            key={step.key}
            className={cn(styles.step, styles[status])}
            aria-current={status === "active" ? "step" : undefined}
          >
            <span className={styles.marker} aria-hidden="true">
              {status === "complete" ? (
                <Icon name="cr:Checkmark" size={14} />
              ) : status === "error" ? (
                <Icon name="cr:Warning" size={14} />
              ) : (
                <span className={styles.index}>{index + 1}</span>
              )}
            </span>
            <span className={styles.body}>
              <span className={styles.label}>{step.label}</span>
              {step.description ? (
                <span className={styles.description}>{step.description}</span>
              ) : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
