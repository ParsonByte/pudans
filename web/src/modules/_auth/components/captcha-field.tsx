import type { ReactNode } from "react";

import type { CaptchaChallenge } from "@pudans-dev/api-client";
import { RefreshIcon, SecuredIcon } from "tdesign-icons-react";
import { Button, Form, Image, Input, Tooltip } from "tdesign-react";

const { FormItem } = Form;

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
        <Image alt="图片验证码" className="h-[60px] w-[180px] rounded-lg" fit="contain" src={challenge.image} />
    ) : null;
    const answerInput = (
        <Input
            autocomplete="off"
            autofocus={autoFocus}
            className={stacked ? "w-full" : "max-w-40"}
            onChange={(value) => onAnswerChange(String(value ?? ""))}
            placeholder={stacked ? "请输入图片验证码" : "输入验证码"}
            prefixIcon={stacked ? <SecuredIcon /> : undefined}
            spellCheck={false}
            value={answer}
        />
    );
    const refreshButton = (
        <Tooltip content="换一张验证码">
            <Button
                aria-label="换一张验证码"
                icon={<RefreshIcon />}
                loading={refreshing}
                onClick={onRefresh}
                shape={stacked ? "circle" : "rectangle"}
                size={stacked ? "large" : "medium"}
            />
        </Tooltip>
    );

    return (
        <FormItem help={resolvedExtra} label={resolvedLabel}>
            {stacked ? (
                <div className="flex flex-col gap-3">
                    <div className="flex items-center justify-center gap-3">
                        {image ? (
                            <div className="overflow-hidden rounded-xl border border-[var(--td-border-level-1-color)] bg-[var(--td-bg-color-container)]">
                                {image}
                            </div>
                        ) : null}
                        {refreshButton}
                    </div>
                    {answerInput}
                </div>
            ) : (
                <div className="flex flex-wrap items-center gap-2">
                    {image}
                    {answerInput}
                    {refreshButton}
                </div>
            )}
        </FormItem>
    );
}
