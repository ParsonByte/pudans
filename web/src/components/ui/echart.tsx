"use client";

import { useEffect, useRef } from "react";

import { BarChart, GraphChart, LineChart, PieChart } from "echarts/charts";
import {
    DatasetComponent,
    GraphicComponent,
    GridComponent,
    LegendComponent,
    MarkLineComponent,
    MarkPointComponent,
    TitleComponent,
    TooltipComponent,
} from "echarts/components";
import * as echarts from "echarts/core";
import type { EChartsCoreOption, EChartsType } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";

import { useThemeSync } from "@/hooks/ui/use-theme-sync";

echarts.use([
    BarChart,
    DatasetComponent,
    GraphicComponent,
    GraphChart,
    GridComponent,
    LegendComponent,
    LineChart,
    MarkLineComponent,
    MarkPointComponent,
    PieChart,
    TitleComponent,
    TooltipComponent,
    CanvasRenderer,
]);

type EChartProps = {
    className?: string;
    height?: number | string;
    option: EChartsCoreOption;
};

export function EChart({ className, height = 320, option }: EChartProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const chartRef = useRef<EChartsType | null>(null);
    const { resolvedTheme } = useThemeSync();

    useEffect(() => {
        const container = containerRef.current;

        if (!container) {
            return;
        }

        const chart = echarts.init(container, undefined, { renderer: "canvas" });
        chartRef.current = chart;
        const observer = new ResizeObserver(() => chart.resize());
        observer.observe(container);

        return () => {
            observer.disconnect();
            chart.dispose();
            chartRef.current = null;
        };
    }, []);

    useEffect(() => {
        chartRef.current?.setOption(option, { notMerge: true });
    }, [option, resolvedTheme]);

    return <div className={className} ref={containerRef} style={{ height, width: "100%" }} />;
}

export type { EChartsCoreOption };
