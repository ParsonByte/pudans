"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, Form, Input } from "antd";
import { KeyRound, UserRound } from "lucide-react";
import Link from "next/link";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";

type AccountLoginFormProps = {
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

type AccountFormValues = {
  password: string;
  username: string;
};

export function AccountLoginForm({ notify, onMfaRequired }: AccountLoginFormProps) {
  const [form] = Form.useForm<AccountFormValues>();
  const [loading, setLoading] = useState(false);

  const submit = async (values: AccountFormValues) => {
    setLoading(true);
    try {
      const { data } = await apiClient.iam.authentication.login({
        loginRequest: {
          password: values.password,
          username: values.username.trim(),
        },
      });
      if (data.mfaRequired && data.mfaChallengeId) {
        onMfaRequired(data.mfaChallengeId);
        return;
      }
      finishLogin(data);
    } catch (reason) {
      notify.error({
        description: apiErrorMessage(reason, "登录失败，请检查账号和密码"),
        title: "登录失败",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      autoComplete="on"
      className="auth-form"
      form={form}
      layout="vertical"
      onFinish={submit}
      requiredMark={false}
      size="large"
    >
      <Form.Item
        name="username"
        rules={[{ message: "请输入账号", required: true }, { whitespace: true }]}
      >
        <Input
          aria-label="账号"
          autoComplete="username"
          placeholder="账号名"
          prefix={<UserRound size={16} />}
        />
      </Form.Item>
      <Form.Item
        name="password"
        rules={[{ message: "请输入密码", required: true }]}
      >
        <Input.Password
          aria-label="密码"
          autoComplete="current-password"
          placeholder="密码"
          prefix={<KeyRound size={16} />}
        />
      </Form.Item>
      <Button block htmlType="submit" loading={loading} type="primary">
        立即登录
      </Button>
      <div className="auth-form-links">
        <Link href="/forgot-password">忘记密码？</Link>
        <span>没有账号？请联系管理员</span>
      </div>
    </Form>
  );
}