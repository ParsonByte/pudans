"use client";

import { useCallback, useRef, useState } from "react";

import { useDownload } from "@/hooks/utils/use-download";

export type QrCodeLevel = "L" | "M" | "Q" | "H";

export type QrCodeOptions = {
    size?: number;
    level?: QrCodeLevel;
    bgColor?: string;
    fgColor?: string;
    marginSize?: number;
};

const DEFAULT_SIZE = 256;
const MIN_SIZE = 64;

export function useQrCode(
    value: string,
    { size: initialSize = DEFAULT_SIZE, level, bgColor, fgColor, marginSize }: QrCodeOptions = {},
) {
    const containerRef = useRef<HTMLDivElement>(null);
    const [size, setSizeState] = useState(initialSize);
    const { download: downloadBlob } = useDownload();

    const resize = useCallback((nextSize: number) => {
        setSizeState(Number.isFinite(nextSize) ? Math.max(MIN_SIZE, Math.floor(nextSize)) : DEFAULT_SIZE);
    }, []);

    const download = useCallback(
        (filename = "qrcode.png", scale = 1) => {
            const canvas = containerRef.current?.querySelector("canvas");
            if (!canvas) {
                return;
            }

            if (scale <= 1) {
                canvas.toBlob((blob) => {
                    if (blob) {
                        downloadBlob(blob, filename);
                    }
                });
                return;
            }

            const output = document.createElement("canvas");
            output.width = Math.round(canvas.width * scale);
            output.height = Math.round(canvas.height * scale);

            const context = output.getContext("2d");
            if (!context) {
                return;
            }

            context.imageSmoothingEnabled = false;
            context.drawImage(canvas, 0, 0, output.width, output.height);
            output.toBlob((blob) => {
                if (blob) {
                    downloadBlob(blob, filename);
                }
            });
        },
        [downloadBlob],
    );

    return {
        containerRef,
        size,
        resize,
        download,
        qrProps: {
            value,
            size,
            level,
            bgColor,
            fgColor,
            marginSize,
        },
    };
}
