"use client";

import { useState } from "react";

import { FingerprintIcon, LockOnIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, Empty, Skeleton, Tag } from "tdesign-react";

import { SectionCard } from "@/components/ui";
import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";

import { useTwoFactorSettingsQuery } from "../../composables/_queries";
import { ChangePasswordDialog } from "./components/change-password-dialog";
import { ConfigureTotpDialog } from "./components/configure-totp-dialog";
import { DevicePanel } from "./components/device-panel";
import { PasskeyPanel } from "./components/passkey-panel";
import { TwoFactorToggleDialog } from "./components/two-factor-toggle-dialog";

type SecurityDialog = "change-password" | "configure-totp" | "disable-two-factor" | "enable-two-factor";

export function SecurityView() {
    const [dialog, setDialog] = useState<SecurityDialog>();
    const twoFactorQuery = useTwoFactorSettingsQuery();
    const settings = twoFactorQuery.data;

    return (
        <>
            <div className="flex min-w-0 flex-col gap-4">
                <div className="grid gap-4 xl:grid-cols-2">
                    <SectionCard
                        extra={
                            <Button icon={<LockOnIcon />} onClick={() => setDialog("change-password")} size="small">
                                修改密码
                            </Button>
                        }
                        title="登录密码"
                    >
                        <p className="m-0 text-sm text-[var(--td-text-color-secondary)]">
                            定期更换登录密码，并避免与其他站点使用相同密码。
                        </p>
                    </SectionCard>

                    <SectionCard
                        extra={
                            <div className="flex flex-wrap items-center gap-2">
                                {settings ? (
                                    <Tag theme={settings.available ? "success" : "default"} variant="light">
                                        {settings.available ? "已启用" : "未启用"}
                                    </Tag>
                                ) : null}
                                {settings?.totpConfigured ? (
                                    <Tag theme="primary" variant="light">
                                        验证器已绑定
                                    </Tag>
                                ) : null}
                            </div>
                        }
                        title="双重验证"
                    >
                        {twoFactorQuery.error ? (
                            <Empty
                                action={
                                    <Button
                                        icon={<RefreshIcon />}
                                        onClick={() => void twoFactorQuery.refetch()}
                                        size="small"
                                    >
                                        重试
                                    </Button>
                                }
                                description={consoleErrorMessage(twoFactorQuery.error)}
                                title="双重验证状态加载失败"
                                type="fail"
                            />
                        ) : null}
                        {twoFactorQuery.isPending ? <Skeleton theme="paragraph" /> : null}
                        {twoFactorQuery.isSuccess && settings ? (
                            <div className="flex flex-col gap-4">
                                <p className="m-0 text-sm text-[var(--td-text-color-secondary)]">
                                    {settings.available
                                        ? "登录时需要额外输入动态口令，可显著降低密码泄露风险。"
                                        : settings.totpConfigured
                                          ? "验证器已绑定，启用后将在登录时要求输入动态口令。"
                                          : "先绑定动态口令验证器，再启用双重验证。"}
                                </p>
                                <div className="flex flex-wrap gap-2">
                                    {!settings.totpConfigured ? (
                                        <Button
                                            icon={<FingerprintIcon />}
                                            onClick={() => setDialog("configure-totp")}
                                            theme="primary"
                                        >
                                            绑定动态口令
                                        </Button>
                                    ) : null}
                                    {settings.totpConfigured && !settings.enabled ? (
                                        <Button onClick={() => setDialog("enable-two-factor")} theme="primary">
                                            启用双重验证
                                        </Button>
                                    ) : null}
                                    {settings.enabled ? (
                                        <Button
                                            onClick={() => setDialog("disable-two-factor")}
                                            theme="danger"
                                            variant="outline"
                                        >
                                            停用双重验证
                                        </Button>
                                    ) : null}
                                    {settings.totpConfigured ? (
                                        <Button onClick={() => setDialog("configure-totp")} variant="outline">
                                            更换验证器
                                        </Button>
                                    ) : null}
                                </div>
                            </div>
                        ) : null}
                    </SectionCard>
                </div>

                <PasskeyPanel />
                <DevicePanel />
            </div>

            {dialog === "change-password" ? <ChangePasswordDialog onClose={() => setDialog(undefined)} /> : null}
            {dialog === "configure-totp" ? (
                <ConfigureTotpDialog
                    onClose={() => setDialog(undefined)}
                    replaceExisting={Boolean(settings?.totpConfigured)}
                />
            ) : null}
            {dialog === "enable-two-factor" || dialog === "disable-two-factor" ? (
                <TwoFactorToggleDialog enabled={dialog === "disable-two-factor"} onClose={() => setDialog(undefined)} />
            ) : null}
        </>
    );
}
