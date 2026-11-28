"use client";

import { useEffect, useState, type CSSProperties } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, Form, Input, Segmented, notification, theme } from "antd";
import { ArrowLeft, CheckCircle2, Mail, Smartphone } from "lucide-react";
import Link from "next/link";

import { apiErrorMessage } from "./auth-api";

import "./auth-page.modules.less";

type ResetStep = "request" | "verify" | "done";
type ResetChannel = "mail" | "sms";

type RequestValues = {
  identifier: string;
};

type ResetValues = {
  confirmPassword: string;
  newPassword: string;
  token: string;
};

const HOME_URL = process.env.NEXT_PUBLIC_HOME_URL;

export function ForgotPasswordPage() {
  const { token } = theme.useToken();
  const [requestForm] = Form.useForm<RequestValues>();
  const [resetForm] = Form.useForm<ResetValues>();
  const [notify, notificationContext] = notification.useNotification({
    placement: "topRight",
  });
  const [step, setStep] = useState<ResetStep>("request");
  const [channel, setChannel] = useState<ResetChannel>("mail");
  const [identifier, setIdentifier] = useState("");
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const style = {
    "--auth-primary": token.colorPrimary,
    "--auth-primary-soft": token.colorPrimaryBg,
    "--auth-border": token.colorBorder,
    "--auth-border-soft": token.colorBorderSecondary,
    "--auth-text": token.colorText,
    "--auth-muted": token.colorTextSecondary,
    "--auth-bg": token.colorBgLayout,
  } as CSSProperties;

  useEffect(() => {
    if (countdown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const goToVerify = (values: RequestValues) => {
    setIdentifier(values.identifier.trim());
    setStep("verify");
    setCountdown(0);
  };

  const sendCode = async () => {
    setSending(true);
    try {
      await apiClient.iam.authentication.forgotPassword({
        forgotPasswordRequest: {
          channel,
          identifier,
        },
      });
      notify.success({
        description: `验证码已发送至 ${identifier} 对应的${channel === "mail" ? "邮箱" : "手机号"}。`,
        title: "验证码已发送",
      });
      setCountdown(60);
    } catch (reason) {
      notify.error({
        description: apiErrorMessage(reason, "验证码发送失败，请稍后重试"),
        title: "发送失败",
      });
    } finally {
      setSending(false);
    }
  };

  const resetPassword = async (values: ResetValues) => {
    setLoading(true);
    try {
      await apiClient.iam.authentication.resetPassword({
        passwordResetRequest: {
          channel,
          identifier,
          newPassword: values.newPassword,
          token: values.token.trim(),
        },
      });
      setStep("done");
      notify.success({
        description: "请使用新密码重新登录。",
        title: "密码已重置",
      });
    } catch (reason) {
      notify.error({
        description: apiErrorMessage(reason, "密码重置失败，请检查验证码"),
        title: "重置失败",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="auth-shell auth-shell--forgot" style={style}>
      {notificationContext}
      <div aria-hidden="true" className="auth-orbit auth-orbit--one" />
      <div aria-hidden="true" className="auth-orbit auth-orbit--two" />
      <header className="auth-topbar auth-topbar--forgot">
        <a className="auth-logo" href={HOME_URL}>
          <img alt="" src="/ui-assets/pudans.svg" />
          <span>PUDANS</span>
        </a>
        <span className="auth-topbar-title">找回密码</span>
      </header>

      <section className="auth-forgot-frame">
        <Link className="auth-back-link" href="/">
          <ArrowLeft size={14} /> 返回登录
        </Link>
        <h1>找回密码</h1>
        <p className="auth-forgot-copy">验证账号归属后即可设置新密码。</p>

        {step === "request" ? (
          <Form
            className="auth-form auth-forgot-form"
            form={requestForm}
            layout="vertical"
            onFinish={goToVerify}
            requiredMark={false}
            size="large"
          >
            <Form.Item
              label="登录名"
              name="identifier"
              rules={[{ message: "请输入账号、邮箱或手机号", required: true }]}
            >
              <Input autoComplete="username" placeholder="输入账号名、邮箱或手机号" />
            </Form.Item>
            <Button block htmlType="submit" type="primary">
              下一步：验证身份
            </Button>
          </Form>
        ) : null}

        {step === "verify" ? (
          <Form
            className="auth-form auth-forgot-form"
            form={resetForm}
            layout="vertical"
            onFinish={resetPassword}
            requiredMark={false}
            size="large"
          >
            <div className="auth-forgot-hint">
              正在验证 <strong>{identifier}</strong> 的账号归属。
            </div>
            <Form.Item label="验证方式">
              <Segmented
                block
                onChange={(value) => {
                  setChannel(value as ResetChannel);
                  setCountdown(0);
                }}
                options={[
                  { icon: <Mail size={15} />, label: "邮箱验证", value: "mail" },
                  { icon: <Smartphone size={15} />, label: "短信验证", value: "sms" },
                ]}
                value={channel}
              />
            </Form.Item>
            <Form.Item
              label="验证码"
              name="token"
              rules={[{ message: "请输入验证码", required: true }]}
            >
              <Input
                autoComplete="one-time-code"
                maxLength={6}
                placeholder="输入验证码"
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
            <Form.Item
              label="新密码"
              name="newPassword"
              rules={[
                { message: "请输入新密码", required: true },
                { message: "密码长度需为 6-20 位", max: 20, min: 6 },
              ]}
            >
              <Input.Password autoComplete="new-password" placeholder="设置新密码" />
            </Form.Item>
            <Form.Item
              dependencies={["newPassword"]}
              label="确认新密码"
              name="confirmPassword"
              rules={[
                { message: "请再次输入新密码", required: true },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue("newPassword") === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error("两次输入的密码不一致"));
                  },
                }),
              ]}
            >
              <Input.Password autoComplete="new-password" placeholder="再次输入新密码" />
            </Form.Item>
            <Button block htmlType="submit" loading={loading} type="primary">
              重置密码
            </Button>
            <div className="auth-form-links">
              <button
                className="auth-text-button"
                onClick={() => setStep("request")}
                type="button"
              >
                重新验证账号
              </button>
            </div>
          </Form>
        ) : null}

        {step === "done" ? (
          <div className="auth-forgot-done">
            <CheckCircle2 size={44} />
            <h2>密码已重置</h2>
            <p>请使用新密码重新登录。</p>
            <Link href="/">
              <Button block type="primary">返回登录</Button>
            </Link>
          </div>
        ) : null}
      </section>

      <footer className="auth-footer">
        <span>© 2026 PUDANS</span>
        <span>安全登录 · 统一身份</span>
      </footer>
    </main>
  );
}