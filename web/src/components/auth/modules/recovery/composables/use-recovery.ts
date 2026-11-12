import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { authApi } from "../../../../../util/api";
import { isApiError } from "../../../../../util/errors";

export type RecoveryStep = 1 | 2 | 3;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_PATTERN = /^\d{6}$/;
const PASSWORD_PATTERN = /^[A-Za-z0-9!@#$%^&*.?]+$/;

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

  const messageFor = (requestError: unknown): string => {
    if (!isApiError(requestError)) {
      return t("recoveryServiceUnavailable");
    }
    switch (requestError.code) {
      case "recovery-code-invalid":
        return t("recoveryCodeInvalid");
      case "recovery-code-cooldown":
        return t("recoveryCodeCooldown");
      case "recovery-mail-unavailable":
        return t("recoveryMailUnavailable");
      case "recovery-password-invalid":
        return t("recoveryPasswordInvalid");
      default:
        if (requestError.status === 429) {
          return t("recoveryCodeCooldown");
        }
        if (requestError.status === 503) {
          return t("recoveryMailUnavailable");
        }
        return t("recoveryServiceUnavailable");
    }
  };

  const sendCode = async (): Promise<void> => {
    setError(null);
    if (!validateAccount()) {
      return;
    }

    setCodeSending(true);
    try {
      await authApi.sendRecoveryCode(account.trim());
      setCodeSent(true);
    } catch (requestError) {
      setError(messageFor(requestError));
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
      if (!CODE_PATTERN.test(code.trim())) {
        setError(t("recoveryCodeInvalid"));
        return;
      }
      setStep(2);
      return;
    }

    if (step === 2) {
      if (password.length < 8 || password.length > 257 || !PASSWORD_PATTERN.test(password)) {
        setError(t("recoveryPasswordInvalid"));
        return;
      }
      if (password !== confirmPassword) {
        setError(t("recoveryPasswordMismatch"));
        return;
      }

      setLoading(true);
      try {
        await authApi.resetPassword({
          email: account.trim(),
          code: code.trim(),
          newPassword: password,
        });
        setStep(3);
      } catch (requestError) {
        setError(messageFor(requestError));
        if (isApiError(requestError) && requestError.code === "recovery-code-invalid") {
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
