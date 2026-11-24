export function isDocumentFullscreen(): boolean {
    return typeof document !== "undefined" && Boolean(document.fullscreenElement);
}

export async function toggleDocumentFullscreen(): Promise<boolean> {
    if (typeof document === "undefined") {
        return false;
    }

    try {
        if (document.fullscreenElement) {
            await document.exitFullscreen();
            return false;
        }

        await document.documentElement.requestFullscreen?.();
        return isDocumentFullscreen();
    } catch {
        return false;
    }
}
