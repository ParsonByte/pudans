"use client";

import { useState } from "react";

import Link from "next/link";

import { Typography } from "antd";

import { AuthFormCard } from "../../components/auth-form-card";
import { TwoFactorView } from "../two-factor";
import { LoginForm } from "./components/login-form";

type SignInViewProps = {
    redirectTo?: string;
};

export function SignInView({ redirectTo }: SignInViewProps) {
    const [twoFactorUsername, setTwoFactorUsername] = useState<string>();

    if (twoFactorUsername !== undefined) {
        return (
            <TwoFactorView
                onBack={() => setTwoFactorUsername(undefined)}
                redirectTo={redirectTo}
                username={twoFactorUsername}
            />
        );
    }

    return (
        <AuthFormCard description="连接你的数字空间。" eyebrow="欢迎回来" title="继续你的创作">
            <LoginForm onTwoFactorRequired={setTwoFactorUsername} redirectTo={redirectTo} />
            <Typography.Paragraph className="!mb-0 text-center !text-xs" type="secondary">
                登录即代表同意{" "}
                <Link
                    className="text-blue-600 hover:opacity-80 dark:text-blue-400"
                    href="/sign-in?routes=legal&tab=terms"
                >
                    法律协议
                </Link>
            </Typography.Paragraph>
        </AuthFormCard>
    );
}
