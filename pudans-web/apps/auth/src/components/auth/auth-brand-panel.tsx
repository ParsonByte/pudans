"use client";

import { ShieldCheck } from "lucide-react";

import type { AuthNotify } from "./auth-notification";
import { IdpQrLogin } from "./idp-qr-login";

type AuthBrandPanelProps = {
  notify: AuthNotify;
  onMfaRequired: (challengeId: string) => void;
};

export function AuthBrandPanel({ notify, onMfaRequired }: AuthBrandPanelProps) {
  return (
    <aside className="auth-brand">
      <div className="auth-brand-eyebrow">PUDANS IDENTITY</div>
      <h1>登录</h1>
      <IdpQrLogin notify={notify} onMfaRequired={onMfaRequired} />
      <div className="auth-brand-note">
        <ShieldCheck size={15} />
        <span>企业级身份与访问管理</span>
      </div>
    </aside>
  );
}