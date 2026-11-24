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
        let resizeFrame = 0;
        chartRef.current = chart;
        const observer = new ResizeObserver(() => {
            window.cancelAnimationFrame(resizeFrame);
            resizeFrame = window.requestAnimationFrame(() => chart.resize());
        });
        observer.observe(container);
        resizeFrame = window.requestAnimationFrame(() => chart.resize());

        return () => {
            window.cancelAnimationFrame(resizeFrame);
            observer.disconnect();
            chart.dispose();
            chartRef.current = null;
        };
    }, []);

    useEffect(() => {
        const chart = chartRef.current;

        if (!chart) {
            return;
        }

        chart.setOption(option, { notMerge: true });
        const resizeFrame = window.requestAnimationFrame(() => chart.resize());

        return () => window.cancelAnimationFrame(resizeFrame);
    }, [option, resolvedTheme]);

    return <div className={className} ref={containerRef} style={{ height, width: "100%" }} />;
}

export type { EChartsCoreOption };
