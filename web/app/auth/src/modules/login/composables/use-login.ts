import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useToast } from "@pudans/components";
import { apiClient } from "@pudans/api-client";
import { encryptPassword } from "../password-crypto";

function errorMessageFor(code: string | null): string | null {
  switch (code) {
    case "invalid-credential":
      return "errorInvalid";
    case "account-disabled":
      return "errorDisabled";
    case "rate-limit-exceeded":
      return "errorRateLimit";
    case "feishu_disabled":
      return "errorFeishuDisabled";
    case "feishu_not_bound":
      return "errorFeishuNotBound";
    case "feishu_state_invalid":
      return "errorFeishuStateInvalid";
    case "feishu_code_missing":
      return "errorFeishuCodeMissing";
    case "feishu_login_failed":
      return "errorFeishuLoginFailed";
    default:
      return code ? "errorGeneric" : null;
  }
}

export function useLogin() {
  const { t } = useTranslation("auth");
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(() => {
    if (typeof window === "undefined") {
      return null;
    }
    const code = new URLSearchParams(window.location.search).get("error");
    const key = errorMessageFor(code);
    return key ? t(key) : null;
  });

  // A failed server-side redirect can land here with ?error=... already in
  // the URL. Surface it through the global queue as soon as the app mounts.
  const initialErrorRef = useRef(error);
  useEffect(() => {
    if (!initialErrorRef.current) {
      return;
    }
    toast.error(initialErrorRef.current);
    initialErrorRef.current = null;
  }, [toast]);

  const togglePasswordVisibility = (): void => {
    setPasswordVisible((current) => !current);
  };

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    // The form performs the empty-field check so it can notify the user
    // through the global toast queue without coupling the hook to the markup.
    if (!username.trim() || !password) {
      return;
    }

    setLoading(true);
    try {
      const publicKeyResponse = await apiClient.login.getPublicKey();
      const publicKey = publicKeyResponse.data.base64Format;
      if (!publicKey) {
        throw new Error("Missing public key.");
      }

      const encrypted = encryptPassword(password, publicKey);
      const response = await fetch("/login", {
        method: "POST",
        credentials: "include",
        redirect: "follow",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "X-Requested-With": "XMLHttpRequest",
        },
        body: new URLSearchParams({
          username: username.trim(),
          password: encrypted,
          "remember-me": "on",
        }),
      });

      const finalUrl = new URL(response.url, window.location.origin);
      if (finalUrl.pathname.startsWith("/challenges/")) {
        window.location.assign(finalUrl.pathname + finalUrl.search);
        return;
      }

      const errorCode = finalUrl.searchParams.get("error");
      if (errorCode) {
        const message = t(errorMessageFor(errorCode) ?? "errorGeneric");
        setError(message);
        toast.error(message);
        return;
      }

      if (response.ok) {
        window.location.assign("/uc/");
        return;
      }

      const fallback = t("errorGeneric");
      setError(fallback);
      toast.error(fallback);
    } catch (requestError) {
      const message =
        requestError instanceof Error && /crypto|public key/i.test(requestError.message)
          ? t("errorCrypto")
          : t("errorGeneric");
      setError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  return {
    username,
    password,
    passwordVisible,
    loading,
    error,
    setError,
    setUsername,
    setPassword,
    togglePasswordVisibility,
    submit,
  };
}
