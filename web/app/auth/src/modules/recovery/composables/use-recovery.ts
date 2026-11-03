import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { apiClient, getErrorMessage } from "@pudans/api-client";

export type RecoveryStep = 1 | 2 | 3;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function useRecovery() {
  const { t } = useTranslation("auth");
  const [step, setStep] = useState<RecoveryStep>(1);
  const [account, setAccount] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [codeSent, setCodeSent] = useState(false);
  const [codeSending, setCodeSending] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validateAccount = (): boolean => {
    if (!account.trim()) {
      setError(t("recoveryRequired"));
      return false;
    }
    if (!EMAIL_PATTERN.test(account.trim())) {
      setError(t("recoveryEmailInvalid"));
      return false;
    }
    return true;
  };

  const humanizeError = (requestError: unknown): string => {
    const message = getErrorMessage(requestError);
    if (/verification code/i.test(message)) {
      return t("recoveryCodeInvalid");
    }
    if (/wait before/i.test(message)) {
      return t("recoveryCodeCooldown");
    }
    if (/password/i.test(message)) {
      return t("recoveryPasswordMin");
    }
    return message;
  };

  const sendCode = async (): Promise<void> => {
    setError(null);
    if (!validateAccount()) {
      return;
    }

    setCodeSending(true);
    try {
      await apiClient.recovery.sendCode({ email: account.trim() });
      setCodeSent(true);
    } catch (requestError) {
      setError(humanizeError(requestError));
    } finally {
      setCodeSending(false);
    }
  };

  const togglePasswordVisibility = (): void => {
    setPasswordVisible((current) => !current);
  };

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    if (step === 1) {
      if (!validateAccount()) {
        return;
      }
      if (!code.trim()) {
        setError(t("recoveryCodeRequired"));
        return;
      }
      setStep(2);
      return;
    }

    if (step === 2) {
      if (password.length < 8) {
        setError(t("recoveryPasswordMin"));
        return;
      }
      if (password !== confirmPassword) {
        setError(t("recoveryPasswordMismatch"));
        return;
      }

      setLoading(true);
      try {
        await apiClient.recovery.resetPassword({
          email: account.trim(),
          code: code.trim(),
          newPassword: password,
        });
        setStep(3);
      } catch (requestError) {
        const rawMessage = getErrorMessage(requestError);
        setError(humanizeError(requestError));
        if (/verification code/i.test(rawMessage)) {
          setStep(1);
        }
      } finally {
        setLoading(false);
      }
    }
  };

  return {
    step,
    account,
    setAccount,
    code,
    setCode,
    codeSent,
    codeSending,
    password,
    setPassword,
    confirmPassword,
    setConfirmPassword,
    passwordVisible,
    togglePasswordVisibility,
    loading,
    error,
    sendCode,
    submit,
  };
}
