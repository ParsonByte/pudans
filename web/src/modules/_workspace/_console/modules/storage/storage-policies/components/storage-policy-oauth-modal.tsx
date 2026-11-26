"use client";

import { useState } from "react";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Button, Input, Tag } from "tdesign-react";

import { GlobalDialog, useDesignTokens } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";

import { CopyableIdentifier } from "../../../../components/copyable-identifier";
import { consoleErrorMessage } from "../../../../composables/_errors";
import {
    type StoragePolicy,
    authorizeStoragePolicyOAuth,
    getStoragePolicyOAuthStatus,
    resolveStoragePolicyOAuthRoot,
} from "../../../../composables/_storage";
import { IdentityDialogFooter } from "../../../identity/components/identity-dialog-footer";
import { STORAGE_POLICIES_QUERY_ROOT, storagePolicyDisplayName } from "../composables/_storage-policies";

type StoragePolicyOAuthModalProps = {
    onClose: () => void;
    policy: StoragePolicy;
};

export function StoragePolicyOAuthModal({ onClose, policy }: StoragePolicyOAuthModalProps) {
    const prompt = useMessagePrompt();
    const token = useDesignTokens();
    const [authorizing, setAuthorizing] = useState(false);
    const [redirectUri, setRedirectUri] = useState<string>();
    const [siteUrl, setSiteUrl] = useState("");
    const [driveId, setDriveId] = useState<string>();
    const [waiting, setWaiting] = useState(false);
    const statusQuery = useQuery({
        queryKey: [...STORAGE_POLICIES_QUERY_ROOT, "oauth-status", policy.metadata.name],
        queryFn: () => getStoragePolicyOAuthStatus(policy.metadata.name),
        refetchInterval: (query) => (waiting && !query.state.data?.authorized ? 5000 : false),
    });
    const resolveRoot = useMutation({
        mutationFn: () => resolveStoragePolicyOAuthRoot(policy.metadata.name, siteUrl.trim()),
        onError: (error) => prompt.error({ content: consoleErrorMessage(error) }),
        onSuccess: (result) => {
            if (result) {
                setDriveId(result);
                return;
            }
            prompt.warning({ content: "未解析到驱动器 ID。" });
        },
    });
    const authorized = statusQuery.data?.authorized === true;
    const authorize = async () => {
        const popup = window.open("about:blank", "_blank");

        if (!popup) {
            prompt.warning({ content: "浏览器阻止了授权窗口，请允许弹窗后重试。" });
            return;
        }
        popup.opener = null;
        setAuthorizing(true);
        try {
            const result = await authorizeStoragePolicyOAuth(policy.metadata.name);
            setRedirectUri(result.redirectUri);
            setWaiting(true);
            popup.location.href = result.url;
        } catch (error) {
            popup.close();
            prompt.error({ content: consoleErrorMessage(error) });
        } finally {
            setAuthorizing(false);
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    extra={
                        <Button
                            loading={statusQuery.isFetching}
                            onClick={() => void statusQuery.refetch()}
                            variant="outline"
                        >
                            刷新状态
                        </Button>
                    }
                    onCancel={onClose}
                    onSubmit={() => void authorize()}
                    pending={authorizing}
                    submitLabel={authorized ? "重新授权" : "开始授权"}
                />
            }
            onClose={onClose}
            open
            title={`OneDrive 授权 · ${storagePolicyDisplayName(policy)}`}
            width={640}
        >
            <div className="flex flex-col gap-4">
                <div className="rounded-lg p-4" style={{ background: token.colorFillTertiary }}>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="text-sm font-medium">授权状态</div>
                        {statusQuery.isPending ? (
                            <Tag theme="default" variant="light">
                                检查中
                            </Tag>
                        ) : statusQuery.error ? (
                            <Tag theme="danger" variant="light">
                                检查失败
                            </Tag>
                        ) : (
                            <Tag theme={authorized ? "success" : "warning"} variant="light">
                                {authorized ? "已授权" : "未授权"}
                            </Tag>
                        )}
                    </div>
                    <div className="mt-2 text-xs" style={{ color: token.colorTextSecondary }}>
                        {statusQuery.error
                            ? consoleErrorMessage(statusQuery.error)
                            : statusQuery.data?.message || "正在读取 OneDrive 授权状态。"}
                    </div>
                    {redirectUri ? (
                        <div className="mt-3">
                            <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                回调地址
                            </div>
                            <div className="mt-1">
                                <CopyableIdentifier value={redirectUri} />
                            </div>
                        </div>
                    ) : null}
                </div>

                <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                    点击开始授权后会在新窗口打开 Microsoft 登录页，完成登录并同意授权后返回此页面，状态会自动刷新。
                </div>

                {authorized ? (
                    <div
                        className="flex flex-col gap-2 border-t pt-4"
                        style={{ borderColor: token.colorBorderSecondary }}
                    >
                        <div className="text-sm font-medium">解析 SharePoint 驱动器 ID</div>
                        <div className="text-xs" style={{ color: token.colorTextTertiary }}>
                            填写 SharePoint 站点地址可解析对应的驱动器 ID，用于存储策略的驱动器设置。
                        </div>
                        <div className="flex flex-wrap items-center gap-2">
                            <Input
                                onChange={(value) => {
                                    setSiteUrl(value);
                                    setDriveId(undefined);
                                }}
                                placeholder="https://contoso.sharepoint.com/sites/team"
                                style={{ minWidth: 280 }}
                                value={siteUrl}
                            />
                            <Button
                                disabled={!siteUrl.trim()}
                                loading={resolveRoot.isPending}
                                onClick={() => resolveRoot.mutate()}
                                variant="outline"
                            >
                                解析
                            </Button>
                        </div>
                        {driveId ? (
                            <div className="mt-1">
                                <div className="text-[11px]" style={{ color: token.colorTextTertiary }}>
                                    驱动器 ID
                                </div>
                                <div className="mt-1">
                                    <CopyableIdentifier value={driveId} />
                                </div>
                            </div>
                        ) : null}
                    </div>
                ) : null}
            </div>
        </GlobalDialog>
    );
}
