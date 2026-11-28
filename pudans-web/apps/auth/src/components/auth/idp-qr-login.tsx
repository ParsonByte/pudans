"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, QRCode, Spin } from "antd";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";
import { IdpProviderLogin } from "./idp-provider-login";

type QrStatus = "loading" | "pending" | "failed" | "expired";

type IdpQrLoginProps = {
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

type QrSession = {
  state: string;
  url: string;
};

export function IdpQrLogin({ notify, onMfaRequired }: IdpQrLoginProps) {
  const [status, setStatus] = useState<QrStatus>("loading");
  const [session, setSession] = useState<QrSession | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const startQr = async () => {
    setStatus("loading");
    setSession(null);
    setMessage(null);
    try {
      const { data } = await apiClient.iam.authentication.startIdpQrLogin({
        idpQrStartRequest: {},
      });
      if (!data.state || !data.url) {
        throw new Error("二维码信息不完整");
      }
      setSession({ state: data.state, url: data.url });
      setStatus("pending");
    } catch (reason) {
      const description = apiErrorMessage(reason, "扫码登录暂不可用，请稍后重试");
      setStatus("failed");
      setMessage(description);
    }
  };

  useEffect(() => {
    void startQr();
  }, []);

  useEffect(() => {
    if (!session || status !== "pending") {
      return;
    }
    const timer = window.setInterval(async () => {
      try {
        const { data } = await apiClient.iam.authentication.pollIdpQrLogin({
          idpQrPollRequest: { state: session.state },
        });
        if (data.status === "success" && data.login) {
          setStatus("loading");
          if (data.login.mfaRequired && data.login.mfaChallengeId) {
            onMfaRequired(data.login.mfaChallengeId);
            return;
          }
          finishLogin(data.login);
          return;
        }
        if (data.status === "failed" || data.status === "expired") {
          setStatus(data.status);
          setMessage(data.message ?? "二维码已失效，请刷新重试");
        }
      } catch (reason) {
        setStatus("failed");
        setMessage(apiErrorMessage(reason, "扫码登录状态查询失败，请重试"));
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [session, status]);

  return (
    <section className="auth-qr">
      <div className="auth-qr-main">
        <h2 className="auth-qr-title">飞书 APP / 钉钉 APP</h2>
        <div className="auth-qr-frame">
          {status === "pending" && session ? (
            <QRCode bordered={false} size={180} value={session.url} />
          ) : (
            <div className="auth-qr-mask">
              {status === "loading" ? (
                <Spin />
              ) : (
                <div className="auth-qr-empty">
                  <span>{message ?? "二维码暂不可用"}</span>
                  <Button onClick={() => void startQr()} size="small" type="link">
                    刷新二维码
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
      <IdpProviderLogin notify={notify} onMfaRequired={onMfaRequired} />
    </section>
  );
}