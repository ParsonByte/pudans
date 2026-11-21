import type {
  GridComponentOption,
  LegendComponentOption,
  TooltipComponentOption,
} from "echarts/components";

import type { ChartTokens } from "./chart-theme";

export function chartGrid(): GridComponentOption {
  return {
    top: 24,
    right: 16,
    bottom: 8,
    left: 8,
    containLabel: true,
  };
}

export function chartTooltip(tokens: ChartTokens): TooltipComponentOption {
  return {
    confine: true,
    backgroundColor: tokens.popover,
    borderColor: tokens.border,
    borderWidth: 1,
    padding: 10,
    textStyle: {
      color: tokens.popoverForeground,
      fontSize: 12,
    },
    extraCssText: "border-radius: var(--radius);",
  };
}

export function chartLegend(tokens: ChartTokens): LegendComponentOption {
  return {
    bottom: 0,
    icon: "rect",
    itemWidth: 10,
    itemHeight: 10,
    textStyle: {
      color: tokens.mutedForeground,
      fontSize: 12,
    },
  };
}

export function chartAxisLabel(tokens: ChartTokens): {
  color: string;
  fontSize: number;
} {
  return {
    color: tokens.mutedForeground,
    fontSize: 12,
  };
}

export function chartSplitLine(tokens: ChartTokens): {
  lineStyle: { color: string };
} {
  return {
    lineStyle: { color: tokens.border },
  };
}
