"use client";

import type { UserDevice } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { DesktopIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { SectionCard, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatDateTime } from "@/modules/_workspace/_console/composables/_format";

import { UC_DEVICES_QUERY_KEY, useDevicesQuery } from "../../../composables/_queries";

function deviceLabel(userAgent?: string): string {
    if (!userAgent) {
        return "未知设备";
    }

    const system = userAgent.includes("iPhone")
        ? "iPhone"
        : userAgent.includes("Android")
          ? "Android"
          : userAgent.includes("Macintosh")
            ? "macOS"
            : userAgent.includes("Windows")
              ? "Windows"
              : userAgent.includes("Linux")
                ? "Linux"
                : "Web 设备";
    const browser = userAgent.includes("Edg/")
        ? "Edge"
        : userAgent.includes("Firefox/")
          ? "Firefox"
          : userAgent.includes("Chrome/")
            ? "Chrome"
            : userAgent.includes("Safari/")
              ? "Safari"
              : "浏览器";

    return `${system} · ${browser}`;
}

export function DevicePanel() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const devicesQuery = useDevicesQuery();
    const revokeDevice = useMutation({
        mutationFn: async (deviceId: string) => apiClient.device.revokeDevice({ deviceId }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: UC_DEVICES_QUERY_KEY });
            prompt.success({ content: "设备会话已注销。" });
        },
    });
    const devices = devicesQuery.data ?? [];

    const confirmRevoke = (device: UserDevice) => {
        const deviceId = device.device.metadata?.name;

        if (!deviceId) {
            return;
        }

        confirmGlobalDialog({
            confirmText: "注销设备",
            content: "该设备上的登录会话将立即失效，需要重新登录。",
            danger: true,
            onConfirm: () => void revokeDevice.mutateAsync(deviceId),
            title: `注销「${deviceLabel(device.device.spec?.userAgent)}」`,
        });
    };

    return (
        <SectionCard
            extra={
                <Button icon={<RefreshIcon />} onClick={() => void devicesQuery.refetch()} size="small" variant="text">
                    刷新
                </Button>
            }
            title="登录设备"
        >
            {devicesQuery.error ? (
                <Empty description={consoleErrorMessage(devicesQuery.error)} title="设备列表加载失败" type="fail" />
            ) : null}
            {devicesQuery.isPending ? <Skeleton theme="paragraph" /> : null}
            {devicesQuery.isSuccess ? (
                devices.length > 0 ? (
                    <div className="flex flex-col divide-y divide-[var(--td-component-border)]">
                        {devices.map((item) => {
                            const deviceId = item.device.metadata?.name;
                            const lastAccessedAt = item.device.spec?.lastAccessedTime;

                            return (
                                <div
                                    className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
                                    key={deviceId ?? item.device.spec?.sessionId}
                                >
                                    <div className="flex min-w-0 items-start gap-3">
                                        <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--td-bg-color-component)] text-[var(--td-text-color-secondary)]">
                                            <DesktopIcon size={20} />
                                        </span>
                                        <div className="min-w-0">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <Typography.Text className="truncate" strong>
                                                    {deviceLabel(item.device.spec?.userAgent)}
                                                </Typography.Text>
                                                {item.currentDevice ? (
                                                    <Tag theme="primary" variant="light">
                                                        当前设备
                                                    </Tag>
                                                ) : null}
                                                <Tag theme={item.active ? "success" : "default"} variant="light">
                                                    {item.active ? "活跃" : "已过期"}
                                                </Tag>
                                            </div>
                                            <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-[var(--td-text-color-secondary)]">
                                                <span>IP {item.device.spec?.ipAddress || "未知"}</span>
                                                <span>最近访问 {formatDateTime(lastAccessedAt)}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <Button
                                        disabled={item.currentDevice || revokeDevice.isPending}
                                        onClick={() => confirmRevoke(item)}
                                        size="small"
                                        theme="danger"
                                        variant="outline"
                                    >
                                        注销设备
                                    </Button>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <Empty description="暂无登录设备" />
                )
            ) : null}
        </SectionCard>
    );
}
