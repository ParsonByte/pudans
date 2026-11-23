"use client";

import type { FocusEvent } from "react";

import { Divider, Form, Input, Switch } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { USERNAME_PATTERN } from "@/constants/validation";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import {
    type AuthenticationSettings,
    type FeishuProviderSettings,
    type IdentityProviderCard,
    type IdentityProviderKind,
    type OAuth2ProviderSettings,
    type OidcProviderSettings,
    type ProviderEditorValues,
    SECRET_MASK,
    providerNames,
    saveFeishuProvider,
    saveOAuth2Provider,
    saveOidcProvider,
    useUpdateAuthenticationSettings,
} from "../composables/_auth-providers";

type ProviderEditorModalProps = {
    authentication: AuthenticationSettings;
    kind: IdentityProviderKind;
    onClose: () => void;
    provider?: IdentityProviderCard;
};

export function ProviderEditorModal({ authentication, kind, onClose, provider }: ProviderEditorModalProps) {
    const [form] = Form.useForm();
    const updateSettings = useUpdateAuthenticationSettings();
    const pending = updateSettings.isPending;
    const existingNames = providerNames(authentication, provider?.name);

    const submit = async () => {
        const values = form.getFieldsValue(true) as ProviderEditorValues;

        try {
            const nextAuthentication =
                kind === "feishu"
                    ? saveFeishuProvider(authentication, values)
                    : kind === "oidc"
                      ? saveOidcProvider(authentication, provider?.name, values)
                      : saveOAuth2Provider(authentication, provider?.name, values);

            await updateSettings.mutateAsync({
                authentication: nextAuthentication,
                successMessage: provider ? "身份提供商已保存。" : "身份提供商已添加。",
            });
            onClose();
        } catch {
            return;
        }
    };
    const close = () => {
        if (!pending) {
            onClose();
        }
    };

    return (
        <GlobalDialog
            footer={
                <IdentityDialogFooter
                    onCancel={close}
                    onSubmit={() => form.submit()}
                    pending={pending}
                    submitLabel={provider ? "保存更改" : "添加提供商"}
                />
            }
            onClose={close}
            open
            title={editorTitle(kind, Boolean(provider))}
            width={680}
        >
            <Form
                form={form}
                initialData={initialValues(kind, provider)}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                {kind === "feishu" ? (
                    <FeishuFields editing={Boolean(provider)} />
                ) : (
                    <>
                        <ProviderIdentityFields existingNames={existingNames} kind={kind} provider={provider} />
                        <Divider />
                        <div className="grid gap-4 sm:grid-cols-2">
                            <Form.FormItem
                                label="Client ID"
                                name="clientId"
                                rules={[{ message: "请输入 Client ID。", required: true, whitespace: true }]}
                            >
                                <Input />
                            </Form.FormItem>
                            <Form.FormItem label="Client Secret" name="clientSecret">
                                <Input
                                    autocomplete="new-password"
                                    onFocus={selectMaskedSecret}
                                    placeholder="公开客户端可留空"
                                    type="password"
                                />
                            </Form.FormItem>
                        </div>
                        <Divider />
                        {kind === "oidc" ? (
                            <Form.FormItem
                                label="Issuer URI"
                                name="issuerUri"
                                rules={[{ message: "请输入有效的 Issuer URI。", url: true }]}
                            >
                                <Input placeholder="https://id.example.com" />
                            </Form.FormItem>
                        ) : null}
                        <div className="grid gap-4 sm:grid-cols-2">
                            <Form.FormItem
                                label="授权端点"
                                name="authorizationUri"
                                rules={[
                                    { message: "请输入授权端点。", required: true, whitespace: true },
                                    { message: "请输入有效的 URL。", url: true },
                                ]}
                            >
                                <Input placeholder="https://example.com/oauth/authorize" />
                            </Form.FormItem>
                            <Form.FormItem
                                label="令牌端点"
                                name="tokenUri"
                                rules={[
                                    { message: "请输入令牌端点。", required: true, whitespace: true },
                                    { message: "请输入有效的 URL。", url: true },
                                ]}
                            >
                                <Input placeholder="https://example.com/oauth/token" />
                            </Form.FormItem>
                            <Form.FormItem
                                label="用户信息端点"
                                name="userInfoUri"
                                rules={[
                                    { message: "请输入用户信息端点。", required: true, whitespace: true },
                                    { message: "请输入有效的 URL。", url: true },
                                ]}
                            >
                                <Input placeholder="https://example.com/oauth/userinfo" />
                            </Form.FormItem>
                            <Form.FormItem label="用户标识字段" name="userNameAttribute">
                                <Input placeholder={kind === "oidc" ? "sub" : "id"} />
                            </Form.FormItem>
                        </div>
                        <Form.FormItem label="权限范围" name="scopes">
                            <Input placeholder={kind === "oidc" ? "openid profile email" : "可选，空格分隔"} />
                        </Form.FormItem>
                    </>
                )}
                <Form.FormItem label="启用登录" name="enabled">
                    <Switch />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}

type ProviderIdentityFieldsProps = {
    existingNames: Set<string>;
    kind: Exclude<IdentityProviderKind, "feishu">;
    provider?: IdentityProviderCard;
};

function ProviderIdentityFields({ existingNames, kind, provider }: ProviderIdentityFieldsProps) {
    return (
        <div className="grid gap-4 sm:grid-cols-2">
            <Form.FormItem
                label="注册标识"
                name="name"
                rules={[
                    { message: "请输入注册标识。", required: true, whitespace: true },
                    {
                        message: "只能使用小写字母、数字、- 和 .，且必须以字母或数字开头结尾。",
                        pattern: USERNAME_PATTERN,
                    },
                    { message: "注册标识不能超过 63 个字符。", max: 63 },
                    {
                        validator: (value) => {
                            if (
                                typeof value !== "string" ||
                                !value.trim() ||
                                !existingNames.has(value.trim().toLowerCase())
                            ) {
                                return true;
                            }

                            return { message: "该注册标识已被其他身份提供商使用。", result: false };
                        },
                    },
                ]}
            >
                <Input disabled={Boolean(provider)} placeholder={kind === "oidc" ? "corp-oidc" : "github"} />
            </Form.FormItem>
            <Form.FormItem
                label="显示名称"
                name="displayName"
                rules={[
                    { message: "请输入显示名称。", required: true, whitespace: true },
                    { message: "显示名称不能超过 64 个字符。", max: 64 },
                ]}
            >
                <Input />
            </Form.FormItem>
        </div>
    );
}

function FeishuFields({ editing }: { editing: boolean }) {
    return (
        <div className="grid gap-4 sm:grid-cols-2">
            <Form.FormItem
                label="App ID"
                name="appId"
                rules={[{ message: "请输入飞书 App ID。", required: true, whitespace: true }]}
            >
                <Input placeholder="cli_xxxxxxxxxxxxxxxx" />
            </Form.FormItem>
            <Form.FormItem
                label="App Secret"
                name="appSecret"
                rules={[{ message: "请输入飞书 App Secret。", required: true, whitespace: true }]}
            >
                <Input
                    autocomplete="new-password"
                    onFocus={selectMaskedSecret}
                    placeholder={editing ? SECRET_MASK : undefined}
                    type="password"
                />
            </Form.FormItem>
            <Form.FormItem
                label="回调地址"
                name="redirectUri"
                rules={[{ message: "请输入有效的回调地址。", url: true }]}
            >
                <Input />
            </Form.FormItem>
            <Form.FormItem label="权限范围" name="scopes">
                <Input />
            </Form.FormItem>
        </div>
    );
}

function initialValues(kind: IdentityProviderKind, provider?: IdentityProviderCard): ProviderEditorValues {
    if (kind === "feishu") {
        const settings = provider?.settingsProvider as FeishuProviderSettings | undefined;

        return {
            appId: settings?.appId,
            appSecret: settings?.appSecret,
            enabled: settings?.enabled !== false,
            redirectUri: settings?.redirectUri,
            scopes: settings?.scope,
        };
    }

    const settings = provider?.settingsProvider as OAuth2ProviderSettings | OidcProviderSettings | undefined;

    return {
        authorizationUri: settings?.authorizationUri,
        clientId: settings?.clientId,
        clientSecret: settings?.clientSecret,
        displayName: settings?.displayName ?? provider?.displayName,
        enabled: settings?.enabled !== false,
        issuerUri: (settings as OidcProviderSettings | undefined)?.issuerUri,
        name: provider?.name,
        scopes: settings?.scopes ?? (kind === "oidc" ? "openid profile email" : undefined),
        tokenUri: settings?.tokenUri,
        userInfoUri: settings?.userInfoUri,
        userNameAttribute: settings?.userNameAttribute,
    };
}

function selectMaskedSecret(_value: unknown, context: { e: FocusEvent<HTMLInputElement> }): void {
    if (context.e.currentTarget.value === SECRET_MASK) {
        context.e.currentTarget.select();
    }
}

function editorTitle(kind: IdentityProviderKind, editing: boolean): string {
    if (kind === "feishu") {
        return editing ? "配置飞书登录" : "添加飞书登录";
    }
    if (kind === "oidc") {
        return editing ? "编辑 OIDC 提供商" : "添加 OIDC 提供商";
    }

    return editing ? "编辑 OAuth2 提供商" : "添加 OAuth2 提供商";
}
