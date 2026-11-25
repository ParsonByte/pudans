"use client";

import type { ReactNode } from "react";

import { DeleteIcon, FileIcon, HardDiskStorageIcon, ImageIcon } from "tdesign-icons-react";

import { MetricCard } from "@/components/ui";

import { formatBytes, formatCount, formatCountOrDash, formatPercent } from "../../../../composables/_format";
import type { StorageFile } from "../../../../composables/_storage";
import { type DashboardTone, useDashboardTheme } from "../../../overview/dashboard/composables/_theme";
import { isImageStorageFile } from "../composables/_storage-files";

type StorageFilesMetricGridProps = {
    activeTotal?: number;
    currentFiles?: StorageFile[];
    loading: boolean;
    trashedTotal?: number;
};

type StorageFilesMetric = {
    footer?: string;
    icon: ReactNode;
    key: string;
    label: string;
    tone: DashboardTone;
    value: string;
};

export function StorageFilesMetricGrid({
    activeTotal,
    currentFiles,
    loading,
    trashedTotal,
}: StorageFilesMetricGridProps) {
    const { token, tone } = useDashboardTheme();
    const files = currentFiles ?? [];
    const images = files.filter(isImageStorageFile).length;
    const capacity = files.reduce((sum, file) => sum + (file.spec.size ?? 0), 0);
    const metrics: StorageFilesMetric[] = [
        {
            footer:
                activeTotal !== undefined && trashedTotal !== undefined
                    ? `回收站 ${formatCount(trashedTotal)} 个文件`
                    : undefined,
            icon: <FileIcon size={20} />,
            key: "active",
            label: "可用文件",
            tone: "blue",
            value: formatCountOrDash(activeTotal),
        },
        {
            footer: trashedTotal !== undefined ? "可恢复或彻底删除" : undefined,
            icon: <DeleteIcon size={20} />,
            key: "trashed",
            label: "回收站",
            tone: "orange",
            value: formatCountOrDash(trashedTotal),
        },
        {
            footer: currentFiles
                ? files.length > 0
                    ? `占本页 ${formatPercent(images, files.length)}`
                    : "本页暂无文件"
                : undefined,
            icon: <ImageIcon size={20} />,
            key: "images",
            label: "本页图片",
            tone: "purple",
            value: currentFiles ? formatCount(images) : "—",
        },
        {
            footer: currentFiles ? `共 ${formatCount(files.length)} 个文件` : undefined,
            icon: <HardDiskStorageIcon size={20} />,
            key: "capacity",
            label: "本页容量",
            tone: "cyan",
            value: currentFiles ? formatBytes(capacity) : "—",
        },
    ];

    return (
        <>
            {metrics.map((metric) => {
                const colors = tone(metric.tone);

                return (
                    <div className="md:col-span-1 xl:col-span-3" key={metric.key}>
                        <MetricCard
                            footer={
                                metric.footer && !loading ? (
                                    <span style={{ color: token.colorTextSecondary }}>{metric.footer}</span>
                                ) : undefined
                            }
                            icon={metric.icon}
                            label={metric.label}
                            loading={loading}
                            tone={colors}
                            value={metric.value}
                        />
                    </div>
                );
            })}
        </>
    );
}
