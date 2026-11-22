import { type ResetPasswordRequest, apiClient } from "@pudans-dev/api-client";
import { isAxiosError } from "axios";

type CaptchaPayload = {
    captchaChallengeId?: string;
    captchaAnswer?: string;
};

export async function prepareRecovery(): Promise<void> {
    await apiClient.login.getLoginStatus();
}

export async function sendRecoveryCode(email: string, captcha?: CaptchaPayload): Promise<void> {
    await apiClient.passwordRecovery.sendPasswordRecoveryCode({ sendCodeRequest: { email, ...captcha } });
}

export async function resetPassword(input: ResetPasswordRequest): Promise<void> {
    await apiClient.passwordRecovery.resetPasswordWithRecoveryCode({ resetPasswordRequest: input });
}

export function isRecoveryCaptchaError(error: unknown): boolean {
    return (
        isAxiosError<{ code?: string }>(error) &&
        error.response?.status === 400 &&
        error.response.data?.code === "recovery-captcha-invalid"
    );
}

export function recoveryErrorMessage(error: unknown): string {
    if (isAxiosError(error)) {
        if (!error.response) {
            return "无法连接服务器，请稍后重试。";
        }

        if (error.response.status === 400) {
            return "验证码无效或已过期，请重新获取。";
        }

        if (error.response.status === 429) {
            return "验证码发送过于频繁，请稍后再试。";
        }

        if (error.response.status === 503) {
            return "邮件服务暂不可用，请联系实例管理员。";
        }
    }

    return "操作失败，请稍后重试。";
}
