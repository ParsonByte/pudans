"use client";

import { Loader2, RefreshCw } from "lucide-react";

import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export type CaptchaChallengeState = {
  challengeId?: string;
  image?: string;
  expiresInSeconds?: number;
};

type CaptchaFieldProps = {
  challenge: CaptchaChallengeState;
  answer: string;
  onAnswerChange: (value: string) => void;
  onRefresh: () => void;
  refreshing: boolean;
};

export function CaptchaField({
  challenge,
  answer,
  onAnswerChange,
  onRefresh,
  refreshing,
}: CaptchaFieldProps) {
  const minutes =
    challenge.expiresInSeconds && challenge.expiresInSeconds > 0
      ? Math.max(1, Math.round(challenge.expiresInSeconds / 60))
      : null;

  return (
    <FormField
      htmlFor="captcha-answer"
      label="图片验证码"
      hint={
        minutes ? `请输入图片中的字符，验证码约 ${minutes} 分钟内有效。` : "请输入图片中的字符。"
      }
    >
      <div className="flex flex-wrap items-center gap-2">
        {challenge.image ? (
          <img
            src={challenge.image}
            alt="图片验证码"
            width={180}
            height={60}
            className="h-[60px] w-[180px] border bg-card object-contain"
          />
        ) : null}
        <Input
          id="captcha-answer"
          autoComplete="off"
          spellCheck={false}
          placeholder="输入验证码"
          className="w-40"
          value={answer}
          onChange={(event) => onAnswerChange(event.target.value)}
        />
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="换一张验证码"
          title="换一张验证码"
          disabled={refreshing}
          onClick={onRefresh}
        >
          {refreshing ? <Loader2 className="animate-spin" /> : <RefreshCw />}
        </Button>
      </div>
    </FormField>
  );
}
