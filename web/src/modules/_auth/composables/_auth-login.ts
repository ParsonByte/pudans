import {
    type LoginProviderView,
    type LoginStatusView,
    type PasskeyAssertionOptionsVo,
    type SessionView,
    apiClient,
    axiosInstance,
} from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

import { setAuthSession } from "@/stores/_auth/store";

import { encryptLoginPassword } from "./_auth-login-crypto";
import { resolvePostLoginRedirect } from "./_auth-post-login-redirect";
import { getPasskeyAssertion, serializeAssertion } from "./_auth-webauthn";

type ProviderWithUrl = LoginProviderView & { authenticationUrl: string };

export type LoginStatus = LoginStatusView & { loginCaptchaEnabled?: boolean };

export type LoginCaptcha = {
    captchaChallengeId?: string;
    captchaAnswer?: string;
};

export type SignInResult = { kind: "authenticated"; session: SessionView } | { kind: "two-factor"; username?: string };

class LoginError extends Error {}

function readCookie(name: string): string | undefined {
    if (typeof document === "undefined") {
        return undefined;
    }

    const prefix = `${name}=`;
    const entry = document.cookie.split("; ").find((item) => item.startsWith(prefix));

    return entry ? decodeURIComponent(entry.slice(prefix.length)) : undefined;
}

export function csrfHeaders(): Record<string, string> {
    const csrfToken = readCookie("XSRF-TOKEN");

    return csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {};
}

function formHeaders(): Record<string, string> {
    return {
        "Content-Type": "application/x-www-form-urlencoded",
        ...csrfHeaders(),
    };
}

export async function getLoginStatus(): Promise<LoginStatus> {
    return (await apiClient.login.getLoginStatus()).data as LoginStatus;
}

async function getLoginSession(): Promise<SessionView> {
    return (await apiClient.login.getLoginSession()).data;
}

async function getLoginPublicKey(): Promise<string> {
    const { base64Format } = (await apiClient.login.getPublicKey()).data;

    if (!base64Format) {
        throw new LoginError("登录公钥缺失，请稍后重试。");
    }

    return base64Format;
}

export async function getLoginProviders(): Promise<LoginProviderView[]> {
    return (await apiClient.login.listLoginProviders()).data;
}

export async function signIn(username: string, password: string, captcha?: LoginCaptcha): Promise<SignInResult> {
    const status = await getLoginStatus();

    if (status.setupRequired) {
        throw new LoginError("系统尚未完成初始化，请先完成设置。");
    }

    const submittedPassword =
        status.rsaEncryptionEnabled === false
            ? password
            : await encryptLoginPassword(password, await getLoginPublicKey());
    const body = new URLSearchParams({ username, password: submittedPassword });

    if (captcha?.captchaChallengeId) {
        body.set("captchaChallengeId", captcha.captchaChallengeId);
    }

    if (captcha?.captchaAnswer) {
        body.set("captchaAnswer", captcha.captchaAnswer);
    }

    const response = await axiosInstance.post<{ twoFactorRequired?: boolean; username?: string }>("/login", body, {
        headers: formHeaders(),
    });

    if (response.data?.twoFactorRequired) {
        return { kind: "two-factor", username: response.data.username ?? username };
    }

    const session = await getLoginSession();

    if (!session.authenticated) {
        throw new LoginError("用户名或密码错误。");
    }

    return { kind: "authenticated", session };
}

async function getPasskeyOptions(username?: string): Promise<PasskeyAssertionOptionsVo> {
    try {
        return (
            await apiClient.passkey.passkeyAssertionOptions(
                username ? { passkeyAssertionOptionsRequest: { username } } : {},
            )
        ).data;
    } catch (error) {
        if (isAxiosError(error) && error.response?.status === 403) {
            throw new LoginError("通行密钥登录未启用。");
        }

        throw error;
    }
}

export async function signInWithPasskey(username?: string): Promise<SignInResult> {
    const options = await getPasskeyOptions(username);
    const publicKey = options.publicKey;

    if (!publicKey) {
        throw new LoginError("通行密钥选项缺失，请重试。");
    }

    let credential: PublicKeyCredential;

    try {
        credential = await getPasskeyAssertion(publicKey);
    } catch (error) {
        if (error instanceof DOMException && error.name === "NotAllowedError") {
            throw new LoginError("已取消通行密钥登录。");
        }

        throw new LoginError("通行密钥登录失败，请重试。");
    }

    const result = await apiClient.passkey.passkeyAssertionVerify({ body: serializeAssertion(credential) });

    if (result.data.twoFactorRequired) {
        return { kind: "two-factor", username: result.data.username ?? username };
    }

    if (!result.data.authenticated) {
        throw new LoginError("通行密钥登录失败，请重试。");
    }

    const session = await getLoginSession();

    if (!session.authenticated) {
        throw new LoginError("登录状态未生效，请重试。");
    }

    return { kind: "authenticated", session };
}

export async function verifyTwoFactorCode(code: string): Promise<SessionView> {
    try {
        await axiosInstance.post("/challenges/two-factor/totp", new URLSearchParams({ code }), {
            headers: formHeaders(),
        });
    } catch (error) {
        const session = await getLoginSession().catch(() => null);

        if (session?.authenticated) {
            return session;
        }

        throw error;
    }

    const session = await getLoginSession();

    if (!session.authenticated) {
        throw new LoginError("两步验证未通过，请重试。");
    }

    return session;
}

export function completeLogin(session: SessionView, redirectTo?: string, onDenied?: () => void): void {
    if (session.consoleAccessDisallowed) {
        onDenied?.();
        return;
    }

    setAuthSession(session);
    window.location.assign(resolvePostLoginRedirect(redirectTo, session.redirectUrl));
}

export async function signOut(): Promise<void> {
    await axiosInstance.post("/logout", undefined, { headers: csrfHeaders() });
}

export function isTwoFactorSessionExpired(error: unknown): boolean {
    return (
        isAxiosError<{ type?: string }>(error) &&
        error.response?.status === 401 &&
        Boolean(error.response.data?.type?.endsWith("/2fa-required"))
    );
}

export function twoFactorErrorMessage(error: unknown): string {
    if (error instanceof LoginError) {
        return error.message;
    }

    if (isAxiosError(error)) {
        if (!error.response) {
            return "无法连接登录服务，请稍后重试。";
        }

        if (error.response.status === 401) {
            return "验证码无效，请重新输入。";
        }

        if (error.response.status === 429) {
            return "尝试次数过多，请稍后再试。";
        }
    }

    return "验证失败，请稍后重试。";
}

export function providerWithUrl(provider: LoginProviderView): provider is ProviderWithUrl {
    return Boolean(provider.authenticationUrl);
}

export function signInErrorMessage(error: unknown, captchaEnabled = false): string {
    if (error instanceof LoginError) {
        return error.message;
    }

    if (isAxiosError(error)) {
        if (!error.response) {
            return "无法连接登录服务，请稍后重试。";
        }

        if (error.response.status === 401) {
            return captchaEnabled ? "登录失败，请检查用户名、密码和图片验证码。" : "用户名或密码错误。";
        }

        if (error.response.status === 403) {
            return "登录请求被拒绝，请刷新页面后重试。";
        }

        if (error.response.status === 429) {
            return "尝试次数过多，请稍后重试。";
        }
    }

    return "登录失败，请稍后重试。";
}
