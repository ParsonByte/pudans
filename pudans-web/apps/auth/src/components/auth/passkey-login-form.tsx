"use client";

import { useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button } from "antd";
import { Fingerprint } from "lucide-react";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";
import { passkeySupported, requestPasskeyAssertion } from "./auth-webauthn";

type PasskeyLoginFormProps = {
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

export function PasskeyLoginForm({ notify, onMfaRequired }: PasskeyLoginFormProps) {
  const [loading, setLoading] = useState(false);
  const supported = passkeySupported();

  const submit = async () => {
    if (!supported) {
      notify.warning({
        description: "请使用最新版 Chrome、Safari 或 Edge 浏览器",
        title: "当前浏览器不支持通行密钥",
      });
      return;
    }
    setLoading(true);
    try {
      const { data: options } = await apiClient.iam.authentication.passkeyLoginOptions({
        passkeyOptionsRequest: {},
      });
      const assertion = await requestPasskeyAssertion(options);
      const { data } = await apiClient.iam.authentication.loginWithPasskey({
        passkeyLoginRequest: {
          authenticatorData: assertion.authenticatorData,
          challengeId: options.challengeId,
          clientDataJson: assertion.clientDataJson,
          credentialId: assertion.credentialId,
          signature: assertion.signature,
        },
      });
      if (data.mfaRequired && data.mfaChallengeId) {
        onMfaRequired(data.mfaChallengeId);
        return;
      }
      finishLogin(data);
    } catch (reason) {
      if (reason instanceof DOMException && reason.name === "NotAllowedError") {
        notify.warning({
          description: "通行密钥验证已取消，或当前设备没有可用的通行密钥",
          title: "验证未完成",
        });
      } else {
        notify.error({
          description: reason instanceof Error
            ? reason.message
            : apiErrorMessage(reason, "通行密钥登录失败，请重试"),
          title: "通行密钥登录失败",
        });
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-passkey">
      <div className="auth-passkey-visual">
        <span className="auth-passkey-ring" />
        <Fingerprint size={44} />
      </div>
      <h3>使用通行密钥登录</h3>
      <p>使用设备上的指纹、面容或安全密钥完成验证，无需输入密码。</p>
      <Button
        block
        icon={<Fingerprint size={18} />}
        loading={loading}
        onClick={submit}
        type="primary"
      >
        使用通行密钥登录
      </Button>
    </div>
  );
}