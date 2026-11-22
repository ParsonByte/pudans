import axios, { isAxiosError } from "axios";

const API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

export type CaptchaChallenge = {
    challengeId?: string;
    image?: string;
    expiresInSeconds?: number;
};

type CaptchaPayload = {
    captchaChallengeId?: string;
    captchaAnswer?: string;
};

export async function prepareRecovery(): Promise<void> {
    await axios.get(`${API_PREFIX}/login/status`, { withCredentials: true });
}

export async function getCaptchaChallenge(): Promise<CaptchaChallenge> {
    const { data } = await axios.get<CaptchaChallenge>(`${API_PREFIX}/captcha/challenge`, {
        withCredentials: true,
    });

    return data;
}

export async function sendRecoveryCode(email: string, captcha?: CaptchaPayload): Promise<void> {
    await axios.post(`${API_PREFIX}/recovery/send-code`, { email, ...captcha }, { withCredentials: true });
}

export async function resetPassword(input: {
    email: string;
    code: string;
    newPassword: string;
    captchaChallengeId?: string;
    captchaAnswer?: string;
}): Promise<void> {
    await axios.post(`${API_PREFIX}/recovery/reset-password`, input, { withCredentials: true });
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
