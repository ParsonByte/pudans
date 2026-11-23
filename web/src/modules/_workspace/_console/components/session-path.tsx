"use client";

import { ArrowRightIcon, MapRoutePlanningIcon } from "tdesign-icons-react";

import { formatPath } from "../composables/_format";
import { useDashboardTheme } from "../modules/overview/dashboard/composables/_theme";

type SessionPathProps = {
    entryPath?: null | string;
    exitPath?: null | string;
    pageCount?: number;
};

export function SessionPath({ entryPath, exitPath, pageCount = 0 }: SessionPathProps) {
    const { token } = useDashboardTheme();
    const entry = entryPath ?? "";
    const exit = exitPath ?? "";
    const samePage = Boolean(entry) && entry === exit;

    if (!entry && !exit) {
        return (
            <div className="flex min-w-0 items-center gap-2">
                <MapRoutePlanningIcon className="shrink-0" size={14} style={{ color: token.colorTextTertiary }} />
                <span className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                    —
                </span>
            </div>
        );
    }

    return (
        <div className="flex min-w-0 items-center gap-2">
            <MapRoutePlanningIcon className="shrink-0" size={14} style={{ color: token.colorTextTertiary }} />
            <span className="min-w-0 flex-1 truncate font-mono text-[11px]" title={formatPath(entry)}>
                {entry ? formatPath(entry) : "—"}
            </span>
            {samePage ? (
                <span className="shrink-0 text-[10px]" style={{ color: token.colorTextTertiary }}>
                    {pageCount > 1 ? `回环 · ${pageCount} 页` : pageCount === 0 ? "未记录页面" : "单页"}
                </span>
            ) : (
                <>
                    <ArrowRightIcon className="shrink-0" size={13} style={{ color: token.colorTextTertiary }} />
                    <span className="min-w-0 flex-1 truncate font-mono text-[11px]" title={formatPath(exit)}>
                        {exit ? formatPath(exit) : "—"}
                    </span>
                </>
            )}
        </div>
    );
}
