"use client";

import { Fingerprint, Mail, Smartphone, ShieldCheck } from "lucide-react";

export type AuthTab = "account" | "email" | "phone" | "passkey";

type AuthBrandPanelProps = {
  activeTab: AuthTab;
  onSelect: (tab: AuthTab) => void;
};

const OTHER_METHODS = [
  { icon: Mail, key: "email" as const, label: "邮箱验证码" },
  { icon: Smartphone, key: "phone" as const, label: "手机验证码" },
  { icon: Fingerprint, key: "passkey" as const, label: "通行密钥" },
];

export function AuthBrandPanel({ activeTab, onSelect }: AuthBrandPanelProps) {
  return (
    <aside className="auth-brand">
      <div className="auth-brand-eyebrow">PUDANS IDENTITY</div>
      <h1>登录</h1>
      <p className="auth-brand-copy">一个账号，安全连接企业所有应用。</p>
      <div className="auth-brand-rule" />
      <section className="auth-other">
        <h2>其他登录方式</h2>
        <div className="auth-other-list">
          {OTHER_METHODS.map(({ icon: Icon, key, label }) => (
            <button
              className={[
                "auth-other-button",
                activeTab === key ? "auth-other-button--active" : "",
              ].filter(Boolean).join(" ")}
              key={key}
              onClick={() => onSelect(key)}
              type="button"
            >
              <span className="auth-other-icon"><Icon size={17} /></span>
              <span>{label}</span>
            </button>
          ))}
        </div>
      </section>
      <div className="auth-brand-note">
        <ShieldCheck size={15} />
        <span>企业级身份与访问管理</span>
      </div>
    </aside>
  );
}