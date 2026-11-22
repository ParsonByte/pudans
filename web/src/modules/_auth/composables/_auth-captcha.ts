import { type CaptchaChallenge, apiClient } from "@pudans-dev/api-client";

export async function getCaptchaChallenge(): Promise<CaptchaChallenge> {
    return (await apiClient.captcha.getCaptchaChallenge()).data;
}
