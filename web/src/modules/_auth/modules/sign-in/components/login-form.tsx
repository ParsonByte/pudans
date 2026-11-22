"use client";

import { useState } from "react";

import Link from "next/link";

import { LinkOutlined, LockOutlined, LoginOutlined, SafetyCertificateOutlined, UserOutlined } from "@ant-design/icons";
import { NewLark } from "@icon-park/react";
import { useQuery } from "@tanstack/react-query";
import { App, Button, Divider, Flex, Form, Input } from "antd";
import { FingerprintIcon } from "tdesign-icons-react";

import {
    getLoginProviders,
    providerWithUrl,
    signIn,
    signInErrorMessage,
    signInWithPasskey,
} from "../../../composables/_auth-login";
import { resolvePostLoginRedirect } from "../../../composables/_auth-post-login-redirect";

type LoginFormValues = {
    username: string;
    password: string;
};

type LoginFormProps = {
    redirectTo?: string;
};

export function LoginForm({ redirectTo }: LoginFormProps) {
    const { message } = App.useApp();
    const [form] = Form.useForm<LoginFormValues>();
    const [submitting, setSubmitting] = useState(false);
    const [passkeyLoading, setPasskeyLoading] = useState(false);
    const providersQuery = useQuery({
        queryKey: ["login-providers"],
        queryFn: getLoginProviders,
        retry: false,
        staleTime: 5 * 60 * 1000,
    });
    const providers = providersQuery.data ?? [];
    const feishuUrl = providers.find((provider) => provider.name?.toLowerCase() === "feishu")?.authenticationUrl;
    const otherProviders = providers
        .filter((provider) => provider.name?.toLowerCase() !== "feishu")
        .filter(providerWithUrl);

    const completeLogin = (session: Awaited<ReturnType<typeof signIn>>) => {
        if (session.consoleAccessDisallowed) {
            message.error("当前账号无权访问控制台。");
            return;
        }

        window.location.assign(resolvePostLoginRedirect(redirectTo, session.redirectUrl));
    };

    const handleFinish = async (values: LoginFormValues) => {
        setSubmitting(true);

        try {
            completeLogin(await signIn(values.username.trim(), values.password));
        } catch (error) {
            message.error(signInErrorMessage(error));
        } finally {
            setSubmitting(false);
        }
    };

    const handlePasskey = async () => {
        if (typeof window === "undefined" || !window.PublicKeyCredential) {
            message.error("当前浏览器不支持通行密钥登录。");
            return;
        }

        setPasskeyLoading(true);

        try {
            const username = String(form.getFieldValue("username") ?? "").trim();
            completeLogin(await signInWithPasskey(username || undefined));
        } catch (error) {
            message.error(signInErrorMessage(error));
        } finally {
            setPasskeyLoading(false);
        }
    };

    return (
        <Form<LoginFormValues>
            autoComplete="on"
            form={form}
            layout="vertical"
            onFinish={handleFinish}
            requiredMark={false}
            size="large"
        >
            <Form.Item
                label="用户名或邮箱"
                name="username"
                rules={[{ message: "请输入用户名或邮箱。", required: true, whitespace: true }]}
                style={{ marginBottom: 8 }}
            >
                <Input
                    autoCapitalize="off"
                    autoComplete="username"
                    placeholder="请输入用户名或邮箱"
                    prefix={<UserOutlined className="text-neutral-400" />}
                    spellCheck={false}
                />
            </Form.Item>
            <Form.Item label="密码" name="password" rules={[{ message: "请输入密码。", required: true }]}>
                <Input.Password
                    autoComplete="current-password"
                    placeholder="请输入密码"
                    prefix={<LockOutlined className="text-neutral-400" />}
                />
            </Form.Item>
            <div className="-mt-3 mb-4 flex justify-end">
                <Link
                    className="text-xs font-medium text-blue-600 hover:opacity-80 dark:text-blue-400"
                    href="/sign-in?routes=forgot-password"
                >
                    忘记密码？
                </Link>
            </div>
            <div className="mb-5 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-neutral-500 dark:text-neutral-400">
                <div className="flex items-center gap-1.5">
                    <SafetyCertificateOutlined className="text-blue-500" />
                    登录状态将安全保留
                </div>
                <div className="flex items-center gap-1.5">
                    <SafetyCertificateOutlined className="text-blue-500" />
                    新设备登录会提醒你
                </div>
            </div>
            <Button block htmlType="submit" icon={<LoginOutlined />} loading={submitting} type="primary">
                登录并继续
            </Button>

            <Divider className="!my-5 !text-xs" plain>
                或使用其他方式
            </Divider>

            <Flex gap={8} wrap>
                <Button
                    className="flex-1"
                    disabled={submitting}
                    icon={<FingerprintIcon />}
                    loading={passkeyLoading}
                    onClick={() => {
                        void handlePasskey();
                    }}
                    type="default"
                >
                    通行密钥
                </Button>
                <Button
                    aria-label="飞书"
                    className="w-32 shrink-0"
                    icon={<NewLark fill="currentColor" size={20} />}
                    onClick={() => {
                        if (feishuUrl) {
                            window.location.assign(feishuUrl);
                            return;
                        }

                        message.error("飞书未配置");
                    }}
                    title="飞书"
                    type="default"
                />
                {otherProviders.map((provider) => (
                    <Button
                        aria-label={provider.displayName ?? provider.name ?? "外部登录"}
                        className="w-20 shrink-0"
                        icon={<LinkOutlined />}
                        key={`${provider.name}-${provider.authenticationUrl}`}
                        onClick={() => window.location.assign(provider.authenticationUrl)}
                        title={provider.displayName ?? provider.name ?? "外部登录"}
                        type="default"
                    />
                ))}
            </Flex>
        </Form>
    );
}
