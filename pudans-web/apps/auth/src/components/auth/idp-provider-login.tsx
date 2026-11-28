"use client";

import { useEffect, useState } from "react";

import { apiClient } from "@pudans-dev/api-client";
import { Tooltip } from "antd";

import { apiErrorMessage, finishLogin } from "./auth-api";
import type { AuthNotify } from "./auth-notification";

type IdpType = "dingtalk_oauth" | "feishu_oauth";

type IdpProviderLoginProps = {
  className?: string;
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
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

export function IdpProviderLogin({
  className,
  notify,
  onMfaRequired,
}: IdpProviderLoginProps) {
  const [popupSession, setPopupSession] = useState<PopupSession | null>(null);
  const [popupLoading, setPopupLoading] = useState(false);

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
  }, [popupSession, notify, onMfaRequired]);

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
    <div className={["auth-qr-others", className].filter(Boolean).join(" ")}>
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
  );
}