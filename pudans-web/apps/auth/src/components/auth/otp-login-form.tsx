"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, Form, Input } from "antd";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";

type OtpChannel = "mail" | "sms";

type OtpLoginFormProps = {
  channel: OtpChannel;
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

type OtpFormValues = {
  code: string;
  recipient: string;
};

const PLACEHOLDER = {
  mail: "邮箱地址",
  sms: "手机号",
};

export function OtpLoginForm({ channel, notify, onMfaRequired }: OtpLoginFormProps) {
  const [form] = Form.useForm<OtpFormValues>();
  const [countdown, setCountdown] = useState(0);
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(false);
  const placeholder = PLACEHOLDER[channel];

  useEffect(() => {
    if (countdown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const sendCode = async () => {
    try {
      const { recipient } = await form.validateFields(["recipient"]);
      setSending(true);
      await apiClient.iam.authentication.sendLoginOtp({
        otpSendRequest: { channel, recipient: recipient.trim() },
      });
      notify.success({
        description: `验证码已发送至 ${recipient.trim()}，请查收。`,
        title: "验证码已发送",
      });
      setCountdown(60);
    } catch (reason) {
      if (reason && typeof reason === "object" && "errorFields" in reason) {
        return;
      }
      notify.error({
        description: apiErrorMessage(reason, "验证码发送失败，请稍后重试"),
        title: "发送失败",
      });
    } finally {
      setSending(false);
    }
  };

  const submit = async (values: OtpFormValues) => {
    setLoading(true);
    try {
      const { data } = await apiClient.iam.authentication.loginWithOtp({
        otpLoginRequest: {
          channel,
          code: values.code.trim(),
          recipient: values.recipient.trim(),
        },
      });
      if (data.mfaRequired && data.mfaChallengeId) {
        onMfaRequired(data.mfaChallengeId);
        return;
      }
      finishLogin(data);
    } catch (reason) {
      notify.error({
        description: apiErrorMessage(reason, "验证码无效或已过期"),
        title: "登录失败",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form
      className="auth-form"
      form={form}
      layout="vertical"
      onFinish={submit}
      onValuesChange={(changed) => {
        if ("recipient" in changed) {
          setCountdown(0);
        }
      }}
      requiredMark={false}
      size="large"
    >
      <Form.Item
        name="recipient"
        rules={[
          { message: `请输入${placeholder}`, required: true },
          channel === "mail"
            ? { message: "请输入有效的邮箱地址", type: "email" }
            : { message: "请输入有效的手机号", pattern: /^(\+?86)?1[3-9]\d{9}$/u },
        ]}
      >
        <Input
          aria-label={placeholder}
          autoComplete={channel === "mail" ? "email" : "tel"}
          placeholder={placeholder}
        />
      </Form.Item>
      <Form.Item name="code" rules={[{ message: "请输入验证码", required: true }]}>
        <Input
          aria-label="验证码"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="验证码"
          suffix={
            <button
              className="auth-code-button"
              disabled={countdown > 0 || sending}
              onClick={sendCode}
              type="button"
            >
              {countdown > 0 ? `${countdown}s 后重发` : "获取验证码"}
            </button>
          }
        />
      </Form.Item>
      <Button block htmlType="submit" loading={loading} type="primary">
        立即登录
      </Button>
    </Form>
  );
}