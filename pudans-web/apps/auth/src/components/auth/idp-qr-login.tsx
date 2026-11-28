"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Button, QRCode, Spin, Tooltip } from "antd";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";

type IdpType = "dingtalk_oauth" | "feishu_oauth";
type QrStatus = "loading" | "pending" | "failed" | "expired";

type IdpQrLoginProps = {
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

type QrSession = {
  state: string;
  url: string;
};

type PopupSession = {
  popup: Window | null;
  state: string;
};

const PROVIDERS: Record<IdpType, {
  icon: string;
  label: string;
}> = {
  dingtalk_oauth: { icon: "dingtalk", label: "钉钉" },
  feishu_oauth: { icon: "feishu", label: "飞书" },
};

export function IdpQrLogin({ notify, onMfaRequired }: IdpQrLoginProps) {
  const [status, setStatus] = useState<QrStatus>("loading");
  const [session, setSession] = useState<QrSession | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [popupSession, setPopupSession] = useState<PopupSession | null>(null);
  const [popupLoading, setPopupLoading] = useState(false);

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

  useEffect(() => {
    if (!popupSession) {
      return;
    }
    const timer = window.setInterval(async () => {
      if (popupSession.popup?.closed) {
        setPopupSession(null);
        return;
      }
      try {
        const { data } = await apiClient.iam.authentication.pollIdpQrLogin({
          idpQrPollRequest: { state: popupSession.state },
        });
        if (data.status === "success" && data.login) {
          popupSession.popup?.close();
          setPopupSession(null);
          if (data.login.mfaRequired && data.login.mfaChallengeId) {
            onMfaRequired(data.login.mfaChallengeId);
            return;
          }
          finishLogin(data.login);
          return;
        }
        if (data.status === "failed" || data.status === "expired") {
          popupSession.popup?.close();
          setPopupSession(null);
          notify.error({
            description: data.message ?? "授权登录未完成，请重试",
            title: "登录失败",
          });
        }
      } catch (reason) {
        popupSession.popup?.close();
        setPopupSession(null);
        notify.error({
          description: apiErrorMessage(reason, "登录状态查询失败，请重试"),
          title: "登录失败",
        });
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [popupSession]);

  const openProvider = async (type: IdpType) => {
    const popup = window.open("about:blank", "pudans-idp-login",
      "width=520,height=680");
    if (!popup) {
      notify.warning({
        description: "浏览器阻止了登录窗口，请允许弹窗后重试",
        title: "无法打开授权页",
      });
      return;
    }
    setPopupLoading(true);
    popup.document.write("<p style=\"font-family: sans-serif;padding:24px\">正在打开授权页…</p>");
    try {
      const { data } = await apiClient.iam.authentication.startIdpQrLogin({
        idpQrStartRequest: { type },
      });
      if (!data.state || !data.url) {
        throw new Error("授权地址不完整");
      }
      popup.location.href = data.url;
      setPopupSession({ popup, state: data.state });
    } catch (reason) {
      popup.close();
      notify.error({
        description: apiErrorMessage(reason, "无法打开授权页，请稍后重试"),
        title: "登录失败",
      });
    } finally {
      setPopupLoading(false);
    }
  };

  return (
    <section className="auth-qr">
      <div className="auth-qr-main">
        <h2 className="auth-qr-title">飞书 APP / 钉钉 APP</h2>
        <div className="auth-qr-frame">
          {status === "loading" ? (
            <Spin />
          ) : status === "pending" && session ? (
            <QRCode bordered={false} size={180} value={session.url} />
          ) : (
            <div className="auth-qr-empty">
              <span>{message ?? "二维码暂不可用"}</span>
              <Button onClick={() => void startQr()} size="small" type="link">
                刷新二维码
              </Button>
            </div>
          )}
        </div>
      </div>
      {status === "pending" || status === "loading" ? (
        <p className="auth-qr-hint">
          {status === "pending" ? "打开钉钉或飞书 App 扫一扫，在手机上确认登录" : "正在生成二维码"}
        </p>
      ) : null}
      <div className="auth-qr-others">
        <div className="auth-qr-label">其他登录方式</div>
        <div className="auth-qr-providers">
          {(Object.keys(PROVIDERS) as IdpType[]).map((key) => {
            const item = PROVIDERS[key];
            return (
              <Tooltip key={key} title={`${item.label}授权登录`}>
                <button
                  aria-label={`${item.label}授权登录`}
                  className="auth-qr-provider"
                  disabled={popupLoading || !!popupSession}
                  onClick={() => void openProvider(key)}
                  type="button"
                >
                  {key === "feishu_oauth" ? (
                    <img alt="" className="auth-qr-provider-image"
                      src="/ui-assets/feishu.png" />
                  ) : (
                    <span
                      className={`auth-qr-provider-icon auth-qr-provider-icon--${item.icon}`}
                    />
                  )}
                </button>
              </Tooltip>
            );
          })}
        </div>
      </div>
    </section>
  );
}