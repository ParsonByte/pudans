"use client";

import { useEffect, useState } from "react";

import { Button, Form, Input } from "antd";

import { PASSWORD_PATTERN, USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { markSetupCompleted } from "@/stores/_setup";

import { setupErrorMessage, submitSetup } from "../../../composables/_setup";
import { FormSection } from "./form-section";

const EXTERNAL_URL_PATTERN = /^https?:\/\//i;
const DEFAULT_SITE_TITLE = "Pudans";

type SetupFormValues = {
    siteTitle: string;
    externalUrl: string;
    username: string;
    password: string;
    confirmPassword: string;
};

function isValidExternalUrl(value: string): boolean {
    try {
        const url = new URL(value);

        return url.protocol === "http:" || url.protocol === "https:";
    } catch {
        return false;
    }
}

function validateExternalUrl(_: unknown, value?: string) {
    if (!value || isValidExternalUrl(value)) {
        return Promise.resolve();
    }

    return Promise.reject(new Error("请输入以 http:// 或 https:// 开头的有效地址。"));
}

export function SetupForm() {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm<SetupFormValues>();
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        const origin = window.location.origin;

        if (!form.getFieldValue("externalUrl") && EXTERNAL_URL_PATTERN.test(origin)) {
            form.setFieldValue("externalUrl", origin);
        }
    }, [form]);

    const handleFinish = async (values: SetupFormValues) => {
        setSubmitting(true);

        try {
            await submitSetup({
                externalUrl: values.externalUrl.trim(),
                password: values.password,
                siteTitle: values.siteTitle.trim(),
                username: values.username.trim(),
            });
            markSetupCompleted();
            prompt.success("系统初始化完成，请登录。");
        } catch (error) {
            const result = setupErrorMessage(error);
            prompt.error(result.message);

            if (result.setupCompleted) {
                markSetupCompleted();
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Form<SetupFormValues>
            className="flex flex-col gap-5"
            form={form}
            initialValues={{ siteTitle: DEFAULT_SITE_TITLE }}
            layout="vertical"
            onFinish={handleFinish}
            requiredMark={false}
            size="large"
        >
            <FormSection title="站点信息">
                <div className="grid gap-4 md:grid-cols-2">
                    <Form.Item
                        className="!mb-0"
                        extra="显示在系统页面和通知中。"
                        label="站点名称"
                        name="siteTitle"
                        rules={[
                            { message: "请输入站点名称。", required: true, whitespace: true },
                            { message: "站点名称不能超过 80 个字符。", max: 80 },
                        ]}
                    >
                        <Input autoComplete="organization" maxLength={80} placeholder="例如：Pudans" />
                    </Form.Item>
                    <Form.Item
                        className="!mb-0"
                        extra="需以 http:// 或 https:// 开头。"
                        label="站点地址"
                        name="externalUrl"
                        rules={[
                            { message: "请输入站点地址。", required: true, whitespace: true },
                            { validator: validateExternalUrl },
                        ]}
                    >
                        <Input autoComplete="url" inputMode="url" placeholder="https://example.com" />
                    </Form.Item>
                </div>
            </FormSection>

            <FormSection title="管理员账户">
                <Form.Item
                    className="!mb-0"
                    extra="4–63 位，小写字母、数字、点或中划线。"
                    label="用户名"
                    name="username"
                    rules={[
                        { message: "请输入管理员用户名。", required: true, whitespace: true },
                        { message: "用户名至少需要 4 个字符。", min: 4 },
                        { message: "用户名不能超过 63 个字符。", max: 63 },
                        {
                            message: "用户名只能包含小写字母、数字、点或中划线，并以字母或数字开头和结尾。",
                            pattern: USERNAME_PATTERN,
                        },
                    ]}
                >
                    <Input
                        autoCapitalize="off"
                        autoComplete="username"
                        maxLength={63}
                        placeholder="例如：admin"
                        spellCheck={false}
                    />
                </Form.Item>
                <div className="grid gap-4 md:grid-cols-2">
                    <Form.Item
                        className="!mb-0"
                        extra="8–257 位，支持字母、数字及 !@#$%^&*.?。"
                        label="密码"
                        name="password"
                        rules={[
                            { message: "请输入管理员密码。", required: true },
                            { message: "密码至少需要 8 个字符。", min: 8 },
                            { message: "密码不能超过 257 个字符。", max: 257 },
                            { message: "密码只能包含大小写字母、数字及 !@#$%^&*.?。", pattern: PASSWORD_PATTERN },
                        ]}
                    >
                        <Input.Password autoComplete="new-password" maxLength={257} placeholder="请输入管理员密码" />
                    </Form.Item>
                    <Form.Item
                        className="!mb-0"
                        dependencies={["password"]}
                        label="确认密码"
                        name="confirmPassword"
                        rules={[
                            { message: "请再次输入密码。", required: true },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    return !value || getFieldValue("password") === value
                                        ? Promise.resolve()
                                        : Promise.reject(new Error("两次输入的密码不一致。"));
                                },
                            }),
                        ]}
                    >
                        <Input.Password autoComplete="new-password" placeholder="请再次输入密码" />
                    </Form.Item>
                </div>
            </FormSection>

            <Button
                block
                className="!h-11 !rounded-xl shadow-lg shadow-blue-500/20"
                disabled={submitting}
                htmlType="submit"
                loading={submitting}
                size="large"
                type="primary"
            >
                完成初始化
            </Button>
        </Form>
    );
}
