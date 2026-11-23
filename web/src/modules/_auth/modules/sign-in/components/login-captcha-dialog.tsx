import { useEffect, useState } from "react";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { Button, Form, Loading, Typography } from "tdesign-react";

import { GlobalDialog } from "@/components/ui";
import { useMessagePrompt } from "@/hooks/ui/use-message-prompt";
import { useEventCallback } from "@/hooks/utils/use-event-callback";
import { AUTH_GLASS_CLASS } from "@/modules/_auth/styles/glass";

import { CaptchaField } from "../../../components/captcha-field";
import { getCaptchaChallenge } from "../../../composables/_auth-captcha";
import { type LoginCaptcha, signInErrorMessage } from "../../../composables/_auth-login";

type LoginCaptchaDialogProps = {
    onCancel: () => void;
    onSubmit: (captcha: LoginCaptcha) => Promise<void>;
};

export function LoginCaptchaDialog({ onCancel, onSubmit }: LoginCaptchaDialogProps) {
    const prompt = useMessagePrompt();
    const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
    const [answer, setAnswer] = useState("");
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    const loadCaptcha = useEventCallback(async () => {
        setLoading(true);

        try {
            const nextChallenge = await getCaptchaChallenge();

            if (nextChallenge.challengeId && nextChallenge.image) {
                setChallenge(nextChallenge);
                setAnswer("");
            } else {
                setChallenge(null);
            }
        } catch {
            setChallenge(null);
            prompt.error({ content: "验证码加载失败，请稍后重试。" });
        } finally {
            setLoading(false);
        }
    });

    useEffect(() => {
        void loadCaptcha();
    }, [loadCaptcha]);

    const handleConfirm = async () => {
        if (submitting) {
            return;
        }

        if (!challenge) {
            prompt.error({ content: "验证码尚未加载完成，请稍后重试。" });
            return;
        }

        if (!answer.trim()) {
            prompt.error({ content: "请输入图片验证码。" });
            return;
        }

        setSubmitting(true);

        try {
            await onSubmit({ captchaChallengeId: challenge.challengeId, captchaAnswer: answer.trim() });
        } catch (submitError) {
            const submitErrorMessage = signInErrorMessage(submitError, true);
            await loadCaptcha();
            prompt.error({ content: submitErrorMessage });
        } finally {
            setSubmitting(false);
        }
    };

    const handleCancel = () => {
        if (submitting) {
            return;
        }

        onCancel();
    };

    const handleAnswerChange = (value: string) => {
        setAnswer(value);
    };
    const minutes =
        challenge?.expiresInSeconds && challenge.expiresInSeconds > 0
            ? Math.max(1, Math.round(challenge.expiresInSeconds / 60))
            : null;

    return (
        <GlobalDialog
            className={AUTH_GLASS_CLASS}
            confirmLoading={submitting}
            footer={
                <div className="flex flex-col gap-2">
                    <Button
                        block
                        disabled={!challenge || loading}
                        loading={submitting}
                        onClick={() => {
                            void handleConfirm();
                        }}
                        size="large"
                        theme="primary"
                    >
                        验证并登录
                    </Button>
                    <Button block disabled={submitting} onClick={handleCancel} size="large" variant="text">
                        取消
                    </Button>
                </div>
            }
            onClose={handleCancel}
            open
            title={false}
            width={440}
        >
            <div className="flex flex-col gap-5">
                <h2 className="pr-8 text-lg leading-7 font-semibold text-[var(--td-text-color-primary)]">
                    确认是本人操作
                </h2>
                {challenge ? (
                    <Form layout="vertical">
                        <div className="rounded-2xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-secondarycontainer)] p-4 [&_.t-form__item]:!mb-0">
                            <CaptchaField
                                answer={answer}
                                autoFocus
                                challenge={challenge}
                                extra={null}
                                label={null}
                                onAnswerChange={handleAnswerChange}
                                onRefresh={() => {
                                    void loadCaptcha();
                                }}
                                refreshing={loading}
                                stacked
                            />
                            {minutes ? (
                                <p className="mt-2 text-center text-xs text-[var(--td-text-color-secondary)]">
                                    验证码约 {minutes} 分钟内有效
                                </p>
                            ) : null}
                        </div>
                    </Form>
                ) : (
                    <div className="flex min-h-[152px] flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-[var(--td-border-level-2-color)] bg-[var(--td-bg-color-secondarycontainer)]">
                        {loading ? (
                            <Loading loading size="medium" />
                        ) : (
                            <>
                                <Typography.Text theme="secondary">验证码加载失败。</Typography.Text>
                                <Button
                                    onClick={() => {
                                        void loadCaptcha();
                                    }}
                                >
                                    重新加载
                                </Button>
                            </>
                        )}
                    </div>
                )}
            </div>
        </GlobalDialog>
    );
}
