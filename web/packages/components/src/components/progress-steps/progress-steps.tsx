import type { ReactNode } from "react";
import Box from "@mui/material/Box";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import Typography from "@mui/material/Typography";
import { cn } from "@pudans/utils";

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
 * Task/upload stepper rebuilt with MUI Stepper + StepLabel (body2 labels,
 * caption descriptions, primary active/completed icons, error step icons,
 * divider connector). Mirrors Pages/Tasks TaskProgress styling.
 */
export function ProgressSteps({
  steps,
  current,
  orientation = "horizontal",
  size = "md",
  className,
}: ProgressStepsProps) {
  const activeStep =
    current ?? steps.findIndex((step) => step.status === "active");

  return (
    <Box className={cn(className)}>
      <Stepper
        activeStep={activeStep}
        orientation={orientation}
        sx={{ "& .MuiStepIcon-root": { fontSize: size === "sm" ? 20 : 24 } }}
      >
        {steps.map((step, index) => {
          const status = resolveStatus(step, index, current);
          return (
            <Step key={step.key} completed={status === "complete"}>
              <StepLabel
                error={status === "error"}
                sx={{
                  "& .MuiStepLabel-label": {
                    color:
                      status === "pending" ? "text.secondary" : "text.primary",
                  },
                }}
              >
                {step.label}
                {step.description ? (
                  <Typography
                    component="span"
                    variant="caption"
                    color="text.secondary"
                    sx={{ display: "block" }}
                  >
                    {step.description}
                  </Typography>
                ) : null}
              </StepLabel>
            </Step>
          );
        })}
      </Stepper>
    </Box>
  );
}
