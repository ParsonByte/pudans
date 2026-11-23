"use client";

import type { AuthProvider } from "@pudans-dev/api-client";
import { Divider, Form, Input, Switch, Textarea } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";

import { IdentityDialogFooter } from "../../components/identity-dialog-footer";
import { useUpdateAuthProvider } from "../composables/_auth-providers";

type LegacyProviderValues = {
    authenticationUrl: string;
    bindingUrl?: string;
    description?: string;
    displayName: string;
    helpPage?: string;
    logo?: string;
    name: string;
    rememberMeSupport?: boolean;
    unbindUrl?: string;
    website?: string;
};

type LegacyProviderEditorModalProps = {
    onClose: () => void;
    provider: AuthProvider;
};

export function LegacyProviderEditorModal({ onClose, provider }: LegacyProviderEditorModalProps) {
    const [form] = Form.useForm();
    const updateProvider = useUpdateAuthProvider();
    const pending = updateProvider.isPending;

    const submit = async () => {
        const values = form.getFieldsValue(true) as LegacyProviderValues;

        try {
            await updateProvider.mutateAsync({
                apiVersion: provider.apiVersion,
                kind: provider.kind,
                metadata: { ...provider.metadata, name: provider.metadata.name },
                spec: {
                    ...provider.spec,
                    authType: "OAUTH2",
                    authenticationUrl: values.authenticationUrl.trim(),
                    bindingUrl: values.bindingUrl?.trim() || undefined,
                    description: values.description?.trim() || undefined,
                    displayName: values.displayName.trim(),
                    helpPage: values.helpPage?.trim() || undefined,
                    logo: values.logo?.trim() || undefined,
                    rememberMeSupport: values.rememberMeSupport ?? false,
                    unbindUrl: values.unbindUrl?.trim() || undefined,
                    website: values.website?.trim() || undefined,
                },
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
                    submitLabel="保存更改"
                />
            }
            onClose={close}
            open
            title="编辑身份提供商"
            width={620}
        >
            <Form
                form={form}
                initialData={{
                    authenticationUrl: provider.spec.authenticationUrl,
                    bindingUrl: provider.spec.bindingUrl,
                    description: provider.spec.description,
                    displayName: provider.spec.displayName,
                    helpPage: provider.spec.helpPage,
                    logo: provider.spec.logo,
                    name: provider.metadata.name,
                    rememberMeSupport: provider.spec.rememberMeSupport ?? false,
                    unbindUrl: provider.spec.unbindUrl,
                    website: provider.spec.website,
                }}
                labelAlign="top"
                onSubmit={({ validateResult }) => {
                    if (validateResult === true) {
                        void submit();
                    }
                }}
            >
                <div className="grid gap-4 sm:grid-cols-2">
                    <Form.FormItem label="注册标识" name="name">
                        <Input disabled />
                    </Form.FormItem>
                    <Form.FormItem
                        label="显示名称"
                        name="displayName"
                        rules={[{ message: "请输入显示名称。", required: true, whitespace: true }]}
                    >
                        <Input />
                    </Form.FormItem>
                </div>
                <Form.FormItem
                    label="认证地址"
                    name="authenticationUrl"
                    rules={[
                        { message: "请输入认证地址。", required: true, whitespace: true },
                        { message: "请输入有效的 URL。", url: true },
                    ]}
                >
                    <Input placeholder="https://example.com/oauth/authorize" />
                </Form.FormItem>
                <Form.FormItem label="描述" name="description">
                    <Textarea autosize={{ maxRows: 4, minRows: 2 }} />
                </Form.FormItem>
                <Divider />
                <div className="grid gap-4 sm:grid-cols-2">
                    <Form.FormItem label="Logo 地址" name="logo" rules={[{ message: "请输入有效的 URL。", url: true }]}>
                        <Input />
                    </Form.FormItem>
                    <Form.FormItem label="官网" name="website" rules={[{ message: "请输入有效的 URL。", url: true }]}>
                        <Input />
                    </Form.FormItem>
                    <Form.FormItem
                        label="帮助页"
                        name="helpPage"
                        rules={[{ message: "请输入有效的 URL。", url: true }]}
                    >
                        <Input />
                    </Form.FormItem>
                    <Form.FormItem label="绑定地址" name="bindingUrl">
                        <Input />
                    </Form.FormItem>
                    <Form.FormItem label="解绑地址" name="unbindUrl">
                        <Input />
                    </Form.FormItem>
                </div>
                <Form.FormItem label="支持记住登录" name="rememberMeSupport">
                    <Switch />
                </Form.FormItem>
            </Form>
        </GlobalDialog>
    );
}
