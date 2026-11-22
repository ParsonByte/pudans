import { format } from "date-fns";

const KILOBYTE = 1024;
const BYTE_UNITS = ["KB", "MB", "GB", "TB"];

export function formatDateTime(value?: string | null): string {
    if (!value) {
        return "—";
    }

    const date = new Date(value);

    return Number.isNaN(date.getTime()) ? value : format(date, "yyyy-MM-dd HH:mm:ss");
}

export function formatDuration(value?: number | null): string {
    if (value === undefined || value === null) {
        return "—";
    }

    return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} s`;
}

export function formatBytes(value?: number | null): string {
    if (value === undefined || value === null) {
        return "—";
    }

    if (value < KILOBYTE) {
        return `${value} B`;
    }

    let size = value / KILOBYTE;
    let unitIndex = 0;

    while (size >= KILOBYTE && unitIndex < BYTE_UNITS.length - 1) {
        size /= KILOBYTE;
        unitIndex += 1;
    }

    return `${size.toFixed(1)} ${BYTE_UNITS[unitIndex]}`;
}
