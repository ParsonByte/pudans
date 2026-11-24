"use client";

import type { UserConnection } from "@pudans-dev/api-client";
import { apiClient } from "@pudans-dev/api-client";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { AddIcon, FingerprintIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Tag, Typography } from "tdesign-react";

import { SectionCard, confirmGlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { createPasskeyRegistration } from "@/modules/_auth/composables/_auth-webauthn";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { formatDateTime } from "@/modules/_workspace/_console/composables/_format";

import {
    UC_AUTH_PROVIDERS_QUERY_KEY,
    UC_CONNECTIONS_QUERY_KEY,
    UC_PASSKEYS_QUERY_KEY,
    usePasskeysQuery,
} from "../../../composables/_queries";

function passkeyLabel(passkey: UserConnection): string {
    return passkey.spec?.displayName || "通行密钥";
}

function passkeyErrorMessage(error: unknown): string {
    if (error instanceof DOMException && error.name === "NotAllowedError") {
        return "已取消通行密钥操作。";
    }

    return consoleErrorMessage(error, "通行密钥操作失败，请稍后重试。");
}

export function PasskeyPanel() {
    const prompt = useMessagePrompt();
    const queryClient = useQueryClient();
    const passkeysQuery = usePasskeysQuery();
    const refreshRelatedQueries = async () => {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: UC_PASSKEYS_QUERY_KEY }),
            queryClient.invalidateQueries({ queryKey: UC_CONNECTIONS_QUERY_KEY }),
            queryClient.invalidateQueries({ queryKey: UC_AUTH_PROVIDERS_QUERY_KEY }),
        ]);
    };
    const registerPasskey = useMutation({
        mutationFn: async () => {
            const publicKey = (await apiClient.passkey.passkeyRegistrationOptions()).data.publicKey;

            if (!publicKey) {
                throw new Error("通行密钥注册参数缺失。");
            }

            const credential = await createPasskeyRegistration(publicKey as Record<string, unknown>);

            return apiClient.passkey.passkeyRegistrationVerify({ body: credential });
        },
        onError: (error) => {
            prompt.error({ content: passkeyErrorMessage(error) });
        },
        onSuccess: async () => {
            await refreshRelatedQueries();
            prompt.success({ content: "通行密钥已添加。" });
        },
    });
    const removePasskey = useMutation({
        mutationFn: async (name: string) => apiClient.userConnection.unbindConnection({ name }),
        onError: (error) => {
            prompt.error({ content: consoleErrorMessage(error) });
        },
        onSuccess: async () => {
            await refreshRelatedQueries();
            prompt.success({ content: "通行密钥已移除。" });
        },
    });
    const passkeys = passkeysQuery.data ?? [];

    const confirmRemove = (passkey: UserConnection) => {
        const name = passkey.metadata?.name;

        if (!name) {
            return;
        }

        confirmGlobalDialog({
            confirmText: "移除",
            content: "移除后该设备将无法再使用此通行密钥登录。",
            danger: true,
            onConfirm: () => void removePasskey.mutateAsync(name),
            title: `移除「${passkeyLabel(passkey)}」`,
        });
    };

    return (
        <SectionCard
            extra={
                <Button
                    icon={<AddIcon />}
                    loading={registerPasskey.isPending}
                    onClick={() => registerPasskey.mutate()}
                    size="small"
                    theme="primary"
                >
                    添加通行密钥
                </Button>
            }
            title="通行密钥"
        >
            <Typography.Paragraph className="!mt-0 !text-[var(--td-text-color-secondary)]">
                使用设备指纹、面容或安全密钥免密登录，避免依赖密码。
            </Typography.Paragraph>
            {passkeysQuery.error ? (
                <Empty
                    action={
                        <Button icon={<RefreshIcon />} onClick={() => void passkeysQuery.refetch()} size="small">
                            重试
                        </Button>
                    }
                    description={consoleErrorMessage(passkeysQuery.error)}
                    title="通行密钥加载失败"
                    type="fail"
                />
            ) : null}
            {passkeysQuery.isPending ? <Skeleton theme="paragraph" /> : null}
            {passkeysQuery.isSuccess ? (
                passkeys.length > 0 ? (
                    <div className="grid gap-3 md:grid-cols-2">
                        {passkeys.map((passkey) => (
                            <div
                                className="flex min-w-0 items-start gap-3 rounded-xl border border-[var(--td-component-border)] p-4"
                                key={passkey.metadata?.name ?? passkey.spec?.providerUserId}
                            >
                                <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--td-brand-color-light)] text-[var(--td-brand-color)]">
                                    <FingerprintIcon size={20} />
                                </span>
                                <div className="min-w-0 flex-1">
                                    <div className="truncate text-sm font-medium">{passkeyLabel(passkey)}</div>
                                    <div className="mt-1 flex flex-wrap items-center gap-2">
                                        <Tag theme="primary" variant="light">
                                            通行密钥
                                        </Tag>
                                        <Typography.Text className="text-xs" theme="secondary">
                                            添加于 {formatDateTime(passkey.metadata?.creationTimestamp)}
                                        </Typography.Text>
                                    </div>
                                </div>
                                <Button
                                    disabled={removePasskey.isPending}
                                    onClick={() => confirmRemove(passkey)}
                                    size="small"
                                    theme="danger"
                                    variant="text"
                                >
                                    移除
                                </Button>
                            </div>
                        ))}
                    </div>
                ) : (
                    <Empty description="尚未添加通行密钥" />
                )
            ) : null}
        </SectionCard>
    );
}
