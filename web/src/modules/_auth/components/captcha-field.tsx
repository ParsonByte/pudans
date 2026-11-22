import type { ReactNode } from "react";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { Button, Flex, Form, Image, Input } from "antd";
import { RefreshCw, ShieldCheck } from "lucide-react";

type CaptchaFieldProps = {
    challenge: CaptchaChallenge;
    answer: string;
    onAnswerChange: (value: string) => void;
    onRefresh: () => void;
    refreshing: boolean;
    label?: ReactNode;
    extra?: ReactNode;
    stacked?: boolean;
    autoFocus?: boolean;
};

export function CaptchaField({
    autoFocus,
    challenge,
    answer,
    onAnswerChange,
    onRefresh,
    refreshing,
    label,
    extra,
    stacked = false,
}: CaptchaFieldProps) {
    const minutes =
        challenge.expiresInSeconds && challenge.expiresInSeconds > 0
            ? Math.max(1, Math.round(challenge.expiresInSeconds / 60))
            : null;
    const resolvedLabel = label === undefined ? "图片验证码" : label;
    const resolvedExtra =
        extra === undefined
            ? minutes
                ? `请输入图片中的字符，验证码约 ${minutes} 分钟内有效。`
                : "请输入图片中的字符。"
            : extra;
    const image = challenge.image ? (
        <Image alt="图片验证码" height={60} preview={false} src={challenge.image} width={180} />
    ) : null;
    const answerInput = (
        <Input
            autoComplete="off"
            autoFocus={autoFocus}
            className={stacked ? "w-full" : "max-w-40"}
            onChange={(event) => onAnswerChange(event.target.value)}
            placeholder={stacked ? "请输入图片验证码" : "输入验证码"}
            prefix={stacked ? <ShieldCheck className="size-4 text-neutral-400" /> : undefined}
            spellCheck={false}
            value={answer}
        />
    );
    const refreshButton = (
        <Button
            aria-label="换一张验证码"
            icon={<RefreshCw className="size-4" />}
            loading={refreshing}
            onClick={onRefresh}
            shape={stacked ? "circle" : "default"}
            size={stacked ? "large" : "middle"}
            title="换一张验证码"
        />
    );

    return (
        <Form.Item extra={resolvedExtra} label={resolvedLabel}>
            {stacked ? (
                <Flex gap={12} vertical>
                    <Flex align="center" gap={12} justify="center">
                        {image ? (
                            <div className="overflow-hidden rounded-xl border border-neutral-200 bg-white dark:border-neutral-800 dark:bg-neutral-900">
                                {image}
                            </div>
                        ) : null}
                        {refreshButton}
                    </Flex>
                    {answerInput}
                </Flex>
            ) : (
                <Flex align="center" gap={8} wrap>
                    {image}
                    {answerInput}
                    {refreshButton}
                </Flex>
            )}
        </Form.Item>
    );
}
