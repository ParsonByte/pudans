"use client";

import { useState, type CSSProperties } from "react";

import { notification, theme } from "antd";

import { AccountLoginForm } from "./account-login-form";
import { AuthBrandPanel } from "./auth-brand-panel";
import { MfaVerify } from "./mfa-verify";
import { OtpLoginForm } from "./otp-login-form";
import { PasskeyLoginForm } from "./passkey-login-form";

import "./auth-page.modules.less";

const HOME_URL = process.env.NEXT_PUBLIC_HOME_URL ?? "http://localhost:3000";

type AuthTab = "account" | "email" | "phone" | "passkey";

const TABS: { key: AuthTab; label: string }[] = [
  { key: "account", label: "账密登录" },
  { key: "email", label: "邮箱登录" },
  { key: "phone", label: "手机登录" },
  { key: "passkey", label: "通行密钥" },
];

const MOBILE_TITLES: Record<AuthTab, string> = {
  account: "账密登录",
  email: "邮箱登录",
  phone: "手机登录",
  passkey: "通行密钥登录",
};

const MOBILE_ALTERNATES: Record<AuthTab, AuthTab[]> = {
  account: ["email", "phone"],
  email: ["account", "phone"],
  phone: ["account", "email"],
  passkey: ["account", "email"],
};

export function AuthPage() {
  const { token } = theme.useToken();
  const [notify, notificationContext] = notification.useNotification({
    placement: "topRight",
  });
  const [activeTab, setActiveTab] = useState<AuthTab>("account");
  const [mfaChallengeId, setMfaChallengeId] = useState<string | null>(null);

  const style = {
    "--auth-primary": token.colorPrimary,
    "--auth-primary-soft": token.colorPrimaryBg,
    "--auth-border": token.colorBorder,
    "--auth-border-soft": token.colorBorderSecondary,
    "--auth-text": token.colorText,
    "--auth-muted": token.colorTextSecondary,
    "--auth-bg": token.colorBgLayout,
  } as CSSProperties;

  const selectTab = (tab: AuthTab) => {
    setMfaChallengeId(null);
    setActiveTab(tab);
  };

  return (
    <main className="auth-shell auth-shell--login" style={style}>
      {notificationContext}
      <div aria-hidden="true" className="auth-orbit auth-orbit--one" />
      <div aria-hidden="true" className="auth-orbit auth-orbit--two" />
      <header className="auth-topbar">
        <a className="auth-logo" href={HOME_URL}>
          <img alt="" src="/ui-assets/pudans.svg" />
          <span>PUDANS</span>
        </a>
      </header>

      <section className="auth-frame">
        <AuthBrandPanel notify={notify} onMfaRequired={setMfaChallengeId} />
        <div className="auth-panel">
          {mfaChallengeId ? (
            <MfaVerify
              challengeId={mfaChallengeId}
              notify={notify}
              onCancel={() => setMfaChallengeId(null)}
            />
          ) : (
            <>
              <div className="auth-mobile-head">
                <h1>{MOBILE_TITLES[activeTab]}</h1>
                <p>
                  你也可以{" "}
                  {MOBILE_ALTERNATES[activeTab].map((key, index) => (
                    <span key={key}>
                      {index > 0 ? " 或 " : null}
                      <button
                        className="auth-mobile-alt"
                        onClick={() => selectTab(key)}
                        type="button"
                      >
                        {TABS.find((tab) => tab.key === key)?.label}
                      </button>
                    </span>
                  ))}
                </p>
              </div>
              <div aria-label="登录方式" className="auth-tabs" role="tablist">
                {TABS.map((tab) => (
                  <button
                    aria-selected={activeTab === tab.key}
                    className={[
                      "auth-tab-button",
                      activeTab === tab.key ? "auth-tab-button--active" : "",
                    ].filter(Boolean).join(" ")}
                    key={tab.key}
                    onClick={() => selectTab(tab.key)}
                    role="tab"
                    type="button"
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
              <div className="auth-tab-content">
                {activeTab === "account" ? (
                  <AccountLoginForm notify={notify} onMfaRequired={setMfaChallengeId} />
                ) : null}
                {activeTab === "email" ? (
                  <OtpLoginForm channel="mail" notify={notify}
                    onMfaRequired={setMfaChallengeId} />
                ) : null}
                {activeTab === "phone" ? (
                  <OtpLoginForm channel="sms" notify={notify}
                    onMfaRequired={setMfaChallengeId} />
                ) : null}
                {activeTab === "passkey" ? (
                  <PasskeyLoginForm notify={notify} onMfaRequired={setMfaChallengeId} />
                ) : null}
              </div>
            </>
          )}
        </div>
      </section>

      <footer className="auth-footer">
        <span>© 2026 PUDANS</span>
        <span>安全登录 · 统一身份</span>
      </footer>
    </main>
  );
}