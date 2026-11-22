import { useEffect, useState } from "react";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { Alert, Button, Flex, Form, Modal, Spin, Typography } from "antd";

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
    const [challenge, setChallenge] = useState<CaptchaChallenge | null>(null);
    const [answer, setAnswer] = useState("");
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const loadCaptcha = useEventCallback(async () => {
        setLoading(true);
        setError(null);

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
            setError("验证码尚未加载完成，请稍后重试。");
            return;
        }

        if (!answer.trim()) {
            setError("请输入图片验证码。");
            return;
        }

        setSubmitting(true);
        setError(null);

        try {
            await onSubmit({ captchaChallengeId: challenge.challengeId, captchaAnswer: answer.trim() });
        } catch (submitError) {
            const submitErrorMessage = signInErrorMessage(submitError, true);
            await loadCaptcha();
            setError(submitErrorMessage);
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
        setError(null);
    };
    const minutes =
        challenge?.expiresInSeconds && challenge.expiresInSeconds > 0
            ? Math.max(1, Math.round(challenge.expiresInSeconds / 60))
            : null;

    return (
        <Modal
            centered
            classNames={{ container: AUTH_GLASS_CLASS }}
            closable={!submitting}
            footer={null}
            keyboard={!submitting}
            mask={{ blur: true, closable: !submitting }}
            onCancel={handleCancel}
            open
            title={null}
            width={440}
        >
            <div className="flex flex-col gap-5">
                <h2 className="pr-8 text-lg leading-7 font-semibold text-neutral-900 dark:text-neutral-100">
                    确认是本人操作
                </h2>
                {challenge ? (
                    <Form
                        layout="vertical"
                        onFinish={() => {
                            void handleConfirm();
                        }}
                    >
                        <div className="rounded-2xl border border-neutral-200/80 bg-neutral-50/80 p-4 dark:border-neutral-800 dark:bg-neutral-900/50 [&_.ant-form-item]:mb-0">
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
                                <p className="mt-2 text-center text-xs text-neutral-500 dark:text-neutral-400">
                                    验证码约 {minutes} 分钟内有效
                                </p>
                            ) : null}
                        </div>
                    </Form>
                ) : (
                    <Flex
                        align="center"
                        className="min-h-[152px] rounded-2xl border border-dashed border-neutral-300 bg-neutral-50/60 dark:border-neutral-700 dark:bg-neutral-900/40"
                        gap={10}
                        justify="center"
                        vertical
                    >
                        {loading ? (
                            <Spin />
                        ) : (
                            <>
                                <Typography.Text type="secondary">验证码加载失败。</Typography.Text>
                                <Button
                                    onClick={() => {
                                        void loadCaptcha();
                                    }}
                                >
                                    重新加载
                                </Button>
                            </>
                        )}
                    </Flex>
                )}
                {error ? <Alert showIcon title={error} type="error" /> : null}
                <div className="flex flex-col gap-2">
                    <Button
                        block
                        disabled={!challenge || loading}
                        htmlType="button"
                        loading={submitting}
                        onClick={() => {
                            void handleConfirm();
                        }}
                        size="large"
                        type="primary"
                    >
                        验证并登录
                    </Button>
                    <Button
                        block
                        disabled={submitting}
                        htmlType="button"
                        onClick={handleCancel}
                        size="large"
                        type="text"
                    >
                        取消
                    </Button>
                </div>
            </div>
        </Modal>
    );
}
