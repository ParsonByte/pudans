import { useId, useState, type ReactNode } from "react";
import {
  Accordion as MuiAccordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Button,
  alpha,
  type Theme,
} from "@mui/material";
import { Icon } from "../icon";

export interface AccordionProps {
  title: ReactNode;
  children: ReactNode;
  defaultExpanded?: boolean;
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  /** "card" is the legacy DialogAccordion look; "ghost" drops the tinted surfaces. */
  variant?: "card" | "ghost";
  icon?: ReactNode;
  disabled?: boolean;
  className?: string;
}

/** Legacy DialogAccordion expanded surface (MUI action selected tint). */
function surfaceColor(theme: Theme): string {
  return theme.palette.mode === "light"
    ? alpha(theme.palette.common.black, 0.06)
    : alpha(theme.palette.common.white, 0.09);
}

/** Legacy SummaryButton hover tint (MUI action hover overlay). */
function hoverColor(theme: Theme): string {
  return theme.palette.mode === "light"
    ? alpha(theme.palette.common.black, 0.09)
    : alpha(theme.palette.common.white, 0.13);
}

/**
 * Legacy DialogAccordion: MUI Accordion (disableGutters / elevation 0) with a
 * row-reverse summary whose entire row is a DefaultButton-style MUI Button
 * (32px min-height, CaretDown start icon) and 16px details padding. The tinted
 * surface lives on the summary when collapsed and on the accordion when
 * expanded, exactly like the legacy component.
 */
export function Accordion({
  title,
  children,
  defaultExpanded = false,
  expanded: controlledExpanded,
  onExpandedChange,
  variant = "card",
  icon,
  disabled = false,
  className,
}: AccordionProps) {
  const contentId = useId();
  const headerId = useId();
  const [uncontrolled, setUncontrolled] = useState(defaultExpanded);
  const expanded = controlledExpanded ?? uncontrolled;
  const ghost = variant === "ghost";

  const handleChange = (_event: React.SyntheticEvent, nextExpanded: boolean): void => {
    if (disabled) {
      return;
    }
    if (controlledExpanded === undefined) {
      setUncontrolled(nextExpanded);
    }
    onExpandedChange?.(nextExpanded);
  };

  const caret = (
    <Box
      component="span"
      sx={{
        display: "inline-flex",
        alignItems: "center",
        transition: (theme) =>
          theme.transitions.create("transform", {
            duration: theme.transitions.duration.shortest,
            easing: theme.transitions.easing.easeInOut,
          }),
        transform: expanded ? "rotate(0deg)" : "rotate(-90deg)",
      }}
    >
      <Icon name="pd:CaretDown" size={12} />
    </Box>
  );

  return (
    <Box className={className}>
      <MuiAccordion
        expanded={expanded}
        onChange={handleChange}
        disabled={disabled}
        disableGutters
        elevation={0}
        sx={{
          borderRadius: (theme) => `${theme.shape.borderRadius}px`,
          backgroundColor: ghost ? "transparent" : (theme: Theme) => (expanded ? surfaceColor(theme) : "transparent"),
          boxShadow: "none",
          "&::before": { display: "none" },
          "&:not(:last-child)": { borderBottom: 0 },
        }}
      >
        <AccordionSummary
          aria-controls={contentId}
          id={headerId}
          expandIcon={false}
          sx={{
            flexDirection: "row-reverse",
            minHeight: 0,
            padding: 0,
            "& .MuiAccordionSummary-content": { margin: 0, width: "100%" },
          }}
        >
          <Button
            fullWidth
            startIcon={icon ? (
              <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.5 }}>
                {caret}
                {icon}
              </Box>
            ) : caret}
            sx={{
              justifyContent: "flex-start",
              minHeight: (theme) => theme.spacing(4),
              color: "text.primary",
              backgroundColor: ghost
                ? "transparent"
                : (theme: Theme) => (expanded ? "transparent" : surfaceColor(theme)),
              "&:hover": {
                backgroundColor: ghost ? "transparent" : (theme: Theme) => hoverColor(theme),
              },
              "& .MuiButton-startIcon": { marginLeft: 0 },
            }}
          >
            {title}
          </Button>
        </AccordionSummary>
        <AccordionDetails id={contentId} sx={{ padding: 2 }}>
          {children}
        </AccordionDetails>
      </MuiAccordion>
    </Box>
  );
}

/** Semantic alias kept for migration parity with the legacy DialogAccordion. */
export const DialogAccordion = Accordion;