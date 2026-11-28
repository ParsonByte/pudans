"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, Divider, Form, Input } from "antd";
import { Fingerprint, ShieldCheck } from "lucide-react";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";
import { passkeySupported, requestPasskeyAssertion } from "./auth-webauthn";

type MfaVerifyProps = {
  challengeId: string;
  notify: AuthNotify;
  onCancel: () => void;
};

type MfaFormValues = {
  code: string;
};

export function MfaVerify({ challengeId, notify, onCancel }: MfaVerifyProps) {
  const [form] = Form.useForm<MfaFormValues>();
  const [loading, setLoading] = useState(false);
  const [passkeyLoading, setPasskeyLoading] = useState(false);
  const supported = passkeySupported();

  const submit = async (values: MfaFormValues) => {
    setLoading(true);
    try {
      const { data } = await apiClient.iam.mfa.verify({
        mfaVerifyRequest: { challengeId, code: values.code.trim() },
      });
      finishLogin(data);
    } catch (reason) {
      notify.error({
        description: apiErrorMessage(reason, "动态口令验证失败，请重试"),
        title: "验证失败",
      });
    } finally {
      setLoading(false);
    }
  };

  const verifyWithPasskey = async () => {
    if (!supported) {
      notify.warning({
        description: "请使用支持 WebAuthn 的浏览器",
        title: "当前浏览器不支持通行密钥",
      });
      return;
    }
    setPasskeyLoading(true);
    try {
      const { data: options } = await apiClient.iam.mfa.webAuthnOptions({
        iamMfaWebAuthnOptionsRequest: { challengeId },
      });
      const assertion = await requestPasskeyAssertion(options);
      const { data } = await apiClient.iam.mfa.webAuthnVerify({
        iamMfaWebAuthnVerifyRequest: {
          authenticatorData: assertion.authenticatorData,
          challengeId,
          clientDataJson: assertion.clientDataJson,
          credentialId: assertion.credentialId,
          signature: assertion.signature,
          webAuthnChallengeId: options.challengeId,
        },
      });
      finishLogin(data);
    } catch (reason) {
      if (reason instanceof DOMException && reason.name === "NotAllowedError") {
        notify.warning({
          description: "通行密钥验证已取消",
          title: "验证未完成",
        });
      } else {
        notify.error({
          description: reason instanceof Error
            ? reason.message
            : apiErrorMessage(reason, "通行密钥验证失败，请重试"),
          title: "验证失败",
        });
      }
    } finally {
      setPasskeyLoading(false);
    }
  };

  return (
    <div className="auth-mfa">
      <div className="auth-form-heading">
        <span className="auth-form-heading-icon"><ShieldCheck size={18} /></span>
        <span>二次验证</span>
      </div>
      <p className="auth-mfa-copy">该账号启用了多因素认证，请完成以下任一种验证。</p>
      <Form form={form} layout="vertical" onFinish={submit} requiredMark={false} size="large">
        <Form.Item name="code" rules={[{ message: "请输入动态口令", required: true }]}>
          <Input
            aria-label="动态口令"
            autoComplete="one-time-code"
            maxLength={6}
            placeholder="动态口令"
          />
        </Form.Item>
        <Button block htmlType="submit" loading={loading} type="primary">
          验证并登录
        </Button>
      </Form>
      <Divider className="auth-divider">或</Divider>
      <Button
        block
        icon={<Fingerprint size={18} />}
        loading={passkeyLoading}
        onClick={verifyWithPasskey}
      >
        使用通行密钥验证
      </Button>
      <div className="auth-form-links auth-form-links--center">
        <button className="auth-text-button" onClick={onCancel} type="button">
          返回登录
        </button>
      </div>
    </div>
  );
}