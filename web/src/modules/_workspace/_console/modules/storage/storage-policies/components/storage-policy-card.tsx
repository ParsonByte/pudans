"use client";

import {
    BrowseIcon,
    DeleteIcon,
    EditIcon,
    FolderOpenIcon,
    LinkIcon,
    LoginIcon,
    MoreIcon,
    ServerIcon,
    SettingIcon,
} from "tdesign-icons-react";
import { Button, Card, Dropdown, Tag, Tooltip } from "tdesign-react";
import type { DropdownOption } from "tdesign-react";

import { useDesignTokens } from "@/components/ui";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { formatBytes, formatDateTime } from "../../../../composables/_format";
import type { StoragePolicy } from "../../../../composables/_storage";
import {
    storagePolicyDisplayName,
    storagePolicyLocation,
    storagePolicyTypeMeta,
} from "../composables/_storage-policies";

type StoragePolicyCardProps = {
    onCors?: () => void;
    onDelete?: () => void;
    onDetails: () => void;
    onEdit?: () => void;
    onOAuth?: () => void;
    onTest?: () => void;
    pending: boolean;
    policy: StoragePolicy;
};

export function StoragePolicyCard({
    onCors,
    onDelete,
    onDetails,
    onEdit,
    onOAuth,
    onTest,
    pending,
    policy,
}: StoragePolicyCardProps) {
    const token = useDesignTokens();
    const type = storagePolicyTypeMeta(policy.spec.type);
    const TypeIcon = type.icon;
    const LocationIcon = type.group === "local" ? FolderOpenIcon : ServerIcon;
    const displayName = storagePolicyDisplayName(policy);
    const location = storagePolicyLocation(policy);
    const maxSize = policy.spec.maxSize && policy.spec.maxSize > 0 ? formatBytes(policy.spec.maxSize) : "不限";
    const testedAt = policy.status?.lastTestedAt ? formatDateTime(policy.status.lastTestedAt) : undefined;
    const actions: DropdownOption[] = [
        {
            content: "详情",
            prefixIcon: <BrowseIcon size={16} />,
            value: "details",
        },
    ];

    if (onEdit) {
        actions.push({
            content: "编辑",
            prefixIcon: <EditIcon size={16} />,
            value: "edit",
        });
    }

    if (onTest) {
        actions.push({
            content: "测试连接",
            prefixIcon: <LinkIcon size={16} />,
            value: "test",
        });
    }

    if (onOAuth) {
        actions.push({
            content: "OneDrive 授权",
            prefixIcon: <LoginIcon size={16} />,
            value: "oauth",
        });
    }

    if (onCors) {
        actions.push({
            content: "跨域配置",
            prefixIcon: <SettingIcon size={16} />,
            value: "cors",
        });
    }

    if (onDelete) {
        actions.push({
            content: "删除",
            prefixIcon: <DeleteIcon size={16} />,
            theme: "error",
            value: "delete",
        });
    }

    const handleAction = (item: DropdownOption) => {
        if (item.value === "details") {
            onDetails();
        } else if (item.value === "edit") {
            onEdit?.();
        } else if (item.value === "test") {
            onTest?.();
        } else if (item.value === "oauth") {
            onOAuth?.();
        } else if (item.value === "cors") {
            onCors?.();
        } else if (item.value === "delete") {
            onDelete?.();
        }
    };

    return (
        <Card
            bodyStyle={{ display: "flex", flex: 1, flexDirection: "column", minHeight: 0, padding: 20 }}
            bordered={false}
            className="h-full"
        >
            <div className="flex min-w-0 items-start justify-between gap-3">
                <div className="flex min-w-0 items-start gap-3">
                    <span
                        className="flex size-10 shrink-0 items-center justify-center rounded-2xl"
                        style={{
                            background: type.group === "local" ? token.colorFillTertiary : token.colorPrimaryBg,
                            color: type.group === "local" ? token.colorTextSecondary : token.colorPrimary,
                        }}
                    >
                        <TypeIcon size={20} />
                    </span>
                    <div className="min-w-0">
                        <div className="truncate text-base font-semibold">{displayName}</div>
                        <div className="mt-1 flex flex-wrap items-center gap-1.5">
                            <Tag theme="default" variant="light">
                                {type.label}
                            </Tag>
                            <Tag theme={policy.status?.ready ? "success" : "warning"} variant="light">
                                {policy.status?.ready ? "就绪" : "未就绪"}
                            </Tag>
                            {policy.spec.defaultPolicy ? (
                                <Tag theme="primary" variant="light">
                                    默认
                                </Tag>
                            ) : null}
                        </div>
                    </div>
                </div>
                <Dropdown
                    disabled={pending}
                    onClick={handleAction}
                    options={actions}
                    placement="bottom-right"
                    trigger="click"
                >
                    <Button aria-label={`${displayName} 操作`} icon={<MoreIcon />} shape="square" variant="text" />
                </Dropdown>
            </div>

            <div className="mt-3 min-w-0">
                <CopyableIdentifier
                    className="[&_button]:shrink-0"
                    display={<span className="min-w-0 truncate font-mono text-xs">{policy.metadata.name}</span>}
                    value={policy.metadata.name}
                />
            </div>

            {location ? (
                <div
                    className="mt-3 flex min-w-0 items-center gap-2 text-xs"
                    style={{ color: token.colorTextTertiary }}
                >
                    <LocationIcon className="shrink-0" size={14} />
                    <Tooltip content={location}>
                        <span className="min-w-0 truncate">{location}</span>
                    </Tooltip>
                </div>
            ) : null}

            <div
                className="mt-auto flex flex-col gap-2 border-t pt-4"
                style={{ borderColor: token.colorBorderSecondary }}
            >
                <div className="grid min-w-0 grid-cols-2 gap-3">
                    <PolicyFact label="大小上限" value={maxSize} />
                    {testedAt ? <PolicyFact label="最近测试" value={testedAt} /> : null}
                </div>
            </div>
        </Card>
    );
}

function PolicyFact({ label, value }: { label: string; value: string }) {
    const token = useDesignTokens();

    return (
        <div className="min-w-0">
            <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                {label}
            </div>
            <Tooltip content={value}>
                <div className="mt-0.5 truncate text-xs font-medium" style={{ color: token.colorTextSecondary }}>
                    {value}
                </div>
            </Tooltip>
        </div>
    );
}
