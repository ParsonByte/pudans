"use client";

import { useEffect, useState } from "react";

import { Button, Form, Input } from "tdesign-react";

import { PASSWORD_PATTERN, USERNAME_PATTERN } from "@/constants/validation";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { markSetupCompleted } from "@/stores/_setup";

import { setupErrorMessage, submitSetup } from "../../../composables/_setup";
import { FormSection } from "./form-section";

const { FormItem } = Form;

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

export function SetupForm() {
    const prompt = useMessagePrompt();
    const [form] = Form.useForm();
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        const origin = window.location.origin;

        if (!form.getFieldValue("externalUrl") && EXTERNAL_URL_PATTERN.test(origin)) {
            form.setFieldsValue({ externalUrl: origin });
        }
    }, [form]);

    const handleFinish = async (validateResult: boolean | object) => {
        if (validateResult !== true) {
            return;
        }

        setSubmitting(true);

        try {
            const values = form.getFieldsValue(true) as unknown as SetupFormValues;

            await submitSetup({
                externalUrl: values.externalUrl.trim(),
                password: values.password,
                siteTitle: values.siteTitle.trim(),
                username: values.username.trim(),
            });
            markSetupCompleted();
            prompt.success({ content: "系统初始化完成，请登录。" });
        } catch (error) {
            const result = setupErrorMessage(error);
            prompt.error({ content: result.message });

            if (result.setupCompleted) {
                markSetupCompleted();
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Form
            className="flex flex-col gap-5"
            form={form}
            initialData={{ siteTitle: DEFAULT_SITE_TITLE }}
            labelAlign="top"
            layout="vertical"
            onSubmit={({ validateResult }) => {
                void handleFinish(validateResult);
            }}
            requiredMark={false}
        >
            <FormSection title="站点信息">
                <div className="grid gap-4 md:grid-cols-2">
                    <FormItem
                        className="!mb-0"
                        label="站点名称"
                        name="siteTitle"
                        rules={[
                            { message: "请输入站点名称。", required: true, whitespace: true },
                            { message: "站点名称不能超过 80 个字符。", max: 80 },
                        ]}
                    >
                        <Input autocomplete="organization" maxlength={80} placeholder="例如：Pudans" size="large" />
                    </FormItem>
                    <FormItem
                        className="!mb-0"
                        label="站点地址"
                        name="externalUrl"
                        rules={[
                            { message: "请输入站点地址。", required: true, whitespace: true },
                            {
                                message: "请输入以 http:// 或 https:// 开头的有效地址。",
                                validator: (value) => !value || isValidExternalUrl(String(value)),
                            },
                        ]}
                    >
                        <Input autocomplete="url" placeholder="https://example.com" size="large" type="url" />
                    </FormItem>
                </div>
            </FormSection>

            <FormSection title="管理员账户">
                <FormItem
                    className="!mb-0"
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
                        autocomplete="username"
                        maxlength={63}
                        placeholder="例如：admin"
                        size="large"
                        spellCheck={false}
                    />
                </FormItem>
                <div className="grid gap-4 md:grid-cols-2">
                    <FormItem
                        className="!mb-0"
                        label="密码"
                        name="password"
                        rules={[
                            { message: "请输入管理员密码。", required: true },
                            { message: "密码至少需要 8 个字符。", min: 8 },
                            { message: "密码不能超过 257 个字符。", max: 257 },
                            { message: "密码只能包含大小写字母、数字及 !@#$%^&*.?。", pattern: PASSWORD_PATTERN },
                        ]}
                    >
                        <Input
                            autocomplete="new-password"
                            maxlength={257}
                            placeholder="请输入管理员密码"
                            size="large"
                            type="password"
                        />
                    </FormItem>
                    <FormItem
                        className="!mb-0"
                        label="确认密码"
                        name="confirmPassword"
                        rules={[
                            { message: "请再次输入密码。", required: true },
                            {
                                message: "两次输入的密码不一致。",
                                validator: (value) => !value || form.getFieldValue("password") === value,
                            },
                        ]}
                    >
                        <Input autocomplete="new-password" placeholder="请再次输入密码" size="large" type="password" />
                    </FormItem>
                </div>
            </FormSection>

            <Button block disabled={submitting} loading={submitting} size="large" theme="primary" type="submit">
                完成初始化
            </Button>
        </Form>
    );
}
