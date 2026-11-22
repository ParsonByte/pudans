import { Button, Flex, Form, Image, Input } from "antd";
import { RefreshCw } from "lucide-react";

import type { CaptchaChallenge } from "../../../composables/_auth-recovery";

type CaptchaFieldProps = {
    challenge: CaptchaChallenge;
    answer: string;
    onAnswerChange: (value: string) => void;
    onRefresh: () => void;
    refreshing: boolean;
};

export function CaptchaField({ challenge, answer, onAnswerChange, onRefresh, refreshing }: CaptchaFieldProps) {
    const minutes =
        challenge.expiresInSeconds && challenge.expiresInSeconds > 0
            ? Math.max(1, Math.round(challenge.expiresInSeconds / 60))
            : null;

    return (
        <Form.Item
            extra={minutes ? `请输入图片中的字符，验证码约 ${minutes} 分钟内有效。` : "请输入图片中的字符。"}
            label="图片验证码"
        >
            <Flex align="center" gap={8} wrap>
                {challenge.image ? (
                    <Image alt="图片验证码" height={60} preview={false} src={challenge.image} width={180} />
                ) : null}
                <Input
                    autoComplete="off"
                    className="max-w-40"
                    onChange={(event) => onAnswerChange(event.target.value)}
                    placeholder="输入验证码"
                    spellCheck={false}
                    value={answer}
                />
                <Button
                    aria-label="换一张验证码"
                    icon={<RefreshCw className="size-4" />}
                    loading={refreshing}
                    onClick={onRefresh}
                    title="换一张验证码"
                />
            </Flex>
        </Form.Item>
    );
}
