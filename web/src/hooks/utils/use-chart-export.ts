"use client";

import { useCallback, useState } from "react";

import { useDownload } from "@/hooks/utils/use-download";

export type ChartExportOptions = {
    filename?: string;
    format?: "png" | "svg";
    scale?: number;
    background?: string;
};

const DEFAULT_FILENAME = "chart";
const DEFAULT_SCALE = 2;
const DEFAULT_BACKGROUND = "#ffffff";

function findSvg(element: Element) {
    if (element instanceof SVGSVGElement) {
        return element;
    }
    return element.querySelector("svg");
}

export function useChartExport() {
    const [exporting, setExporting] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const { download } = useDownload();

    const exportChart = useCallback(
        async (element: Element, options: ChartExportOptions = {}) => {
            setExporting(true);
            setError(null);

            try {
                const svg = findSvg(element);
                if (!svg) {
                    throw new Error("未找到可导出的 SVG 图表。");
                }

                const rect = svg.getBoundingClientRect();
                if (rect.width === 0 || rect.height === 0) {
                    throw new Error("图表尺寸为空，无法导出。");
                }

                const format = options.format ?? "png";
                const filename = `${options.filename ?? DEFAULT_FILENAME}.${format}`;
                const clone = svg.cloneNode(true) as SVGSVGElement;
                clone.setAttribute("xmlns", "http://www.w3.org/2000/svg");
                clone.setAttribute("width", String(rect.width));
                clone.setAttribute("height", String(rect.height));

                const serialized = new XMLSerializer().serializeToString(clone);
                const svgBlob = new Blob([serialized], { type: "image/svg+xml;charset=utf-8" });

                if (format === "svg") {
                    download(svgBlob, filename);
                    return;
                }

                const imageUrl = URL.createObjectURL(svgBlob);
                try {
                    const image = new Image();
                    image.src = imageUrl;
                    await image.decode();

                    const scale = options.scale ?? DEFAULT_SCALE;
                    const canvas = document.createElement("canvas");
                    canvas.width = Math.max(1, Math.round(rect.width * scale));
                    canvas.height = Math.max(1, Math.round(rect.height * scale));

                    const context = canvas.getContext("2d");
                    if (!context) {
                        throw new Error("无法创建画布上下文。");
                    }

                    context.fillStyle = options.background ?? DEFAULT_BACKGROUND;
                    context.fillRect(0, 0, canvas.width, canvas.height);
                    context.drawImage(image, 0, 0, canvas.width, canvas.height);

                    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
                    if (!blob) {
                        throw new Error("图表图片生成失败。");
                    }
                    download(blob, filename);
                } finally {
                    URL.revokeObjectURL(imageUrl);
                }
            } catch (cause) {
                const normalized = cause instanceof Error ? cause : new Error(String(cause));
                setError(normalized);
                throw normalized;
            } finally {
                setExporting(false);
            }
        },
        [download],
    );

    return { exporting, error, exportChart };
}
