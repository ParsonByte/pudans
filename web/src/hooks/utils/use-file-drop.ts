"use client";

import { useEffect, useRef, useState } from "react";

import { useEventCallback } from "@/hooks/utils/use-event-callback";

export type FileDropOptions = {
    onDrop: (files: File[], event: DragEvent) => void;
    disabled?: boolean;
};

export function useFileDrop<T extends HTMLElement>({ onDrop, disabled = false }: FileDropOptions) {
    const ref = useRef<T>(null);
    const [isDragging, setIsDragging] = useState(false);
    const dragDepthRef = useRef(0);
    const handleDrop = useEventCallback(onDrop);

    useEffect(() => {
        const element = ref.current;
        if (!element || disabled) {
            return;
        }

        const handleDragEnter = (event: DragEvent) => {
            event.preventDefault();
            dragDepthRef.current += 1;
            setIsDragging(true);
        };
        const handleDragOver = (event: DragEvent) => {
            event.preventDefault();
        };
        const handleDragLeave = (event: DragEvent) => {
            event.preventDefault();
            dragDepthRef.current = Math.max(0, dragDepthRef.current - 1);
            if (dragDepthRef.current === 0) {
                setIsDragging(false);
            }
        };
        const handleDropEvent = (event: DragEvent) => {
            event.preventDefault();
            dragDepthRef.current = 0;
            setIsDragging(false);

            const files = Array.from(event.dataTransfer?.files ?? []);
            if (files.length > 0) {
                handleDrop(files, event);
            }
        };

        element.addEventListener("dragenter", handleDragEnter);
        element.addEventListener("dragover", handleDragOver);
        element.addEventListener("dragleave", handleDragLeave);
        element.addEventListener("drop", handleDropEvent);

        return () => {
            element.removeEventListener("dragenter", handleDragEnter);
            element.removeEventListener("dragover", handleDragOver);
            element.removeEventListener("dragleave", handleDragLeave);
            element.removeEventListener("drop", handleDropEvent);
        };
    }, [disabled, handleDrop]);

    return { ref, isDragging };
}
