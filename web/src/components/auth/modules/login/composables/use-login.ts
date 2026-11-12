import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useToast } from "@pudans/components";
import { authApi } from "../../../../../util/api";
import type { LoginProvider, LoginStatus } from "../../../../../util/api-types";
import { encryptPassword } from "../../../../../util/password-crypto";
import { safeRedirectPath } from "../../../../../util/url";

function errorMessageKey(code: string | null): string | null {
  switch (code) {
    case "invalid-credential":
      return "errorInvalid";
    case "account-disabled":
      return "errorDisabled";
    case "rate-limit-exceeded":
      return "errorRateLimit";
    case "feishu_disabled":
      return "errorProviderDisabled";
    case "feishu_not_bound":
      return "errorProviderNotBound";
    case "feishu_state_invalid":
      return "errorProviderState";
    case "feishu_code_missing":
      return "errorProviderCode";
    case "feishu_login_failed":
      return "errorProviderFailed";
    default:
      return code ? "errorGeneric" : null;
  }
}

function clearErrorQuery(): void {
  if (typeof window === "undefined") {
    return;
  }
  const url = new URL(window.location.href);
  if (!url.searchParams.has("error") && !url.searchParams.has("need_binding")) {
    return;
  }
  url.searchParams.delete("error");
  url.searchParams.delete("need_binding");
  window.history.replaceState({}, "", url.pathname + url.search + url.hash);
}

export function useLogin() {
  const { t } = useTranslation("auth");
  const toast = useToast();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const [loading, setLoading] = useState(false);
  const [providers, setProviders] = useState<LoginProvider[]>([]);
  const [status, setStatus] = useState<LoginStatus | null>(null);
  const [error, setError] = useState<string | null>(() => {
    if (typeof window === "undefined") {
      return null;
    }
    const code = new URLSearchParams(window.location.search).get("error");
    const key = errorMessageKey(code);
    return key ? t(key) : null;
  });

  const initialErrorRef = useRef(error);
  useEffect(() => {
    if (!initialErrorRef.current) {
      return;
    }
    toast.error(initialErrorRef.current);
    initialErrorRef.current = null;
    clearErrorQuery();
  }, [toast]);

  useEffect(() => {
    let active = true;
    void (async () => {
      const [statusResult, providersResult] = await Promise.allSettled([
        authApi.getStatus(),
        authApi.getProviders(),
      ]);
      if (!active) {
        return;
      }
      setStatus(statusResult.status === "fulfilled" ? statusResult.value : null);
      setProviders(
        providersResult.status === "fulfilled"
          ? providersResult.value.filter((provider) => provider.authenticationUrl)
          : [],
      );
    })();
    return () => {
      active = false;
    };
  }, []);

  const togglePasswordVisibility = (): void => {
    setPasswordVisible((current) => !current);
  };

  const showError = (message: string): void => {
    setError(message);
    toast.error(message);
  };

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    setError(null);

    if (!username.trim() || !password) {
      showError(t("errorRequired"));
      return;
    }

    setLoading(true);
    try {
      const publicKeyResponse = await authApi.getPublicKey();
      const publicKey = publicKeyResponse.base64Format;
      if (!publicKey) {
        throw new Error("The server returned an empty public key.");
      }

      const encrypted = encryptPassword(password, publicKey);
      const outcome = await authApi.submitLogin(username.trim(), encrypted);

      if (outcome.kind === "challenge") {
        window.location.assign(outcome.location);
        return;
      }
      if (outcome.kind === "error") {
        showError(t(errorMessageKey(outcome.code) ?? "errorGeneric"));
        return;
      }

      let redirectUrl = status?.postLoginUrl ?? "/auth/done";
      try {
        const session = await authApi.getSession();
        redirectUrl = session.redirectUrl || redirectUrl;
      } catch {
        // The session snapshot is an optimisation; the server-provided default
        // remains a valid destination.
      }
      window.location.assign(safeRedirectPath(redirectUrl, "/auth/done"));
    } catch (requestError) {
      const cryptoFailure =
        requestError instanceof Error && /public key|crypto|password is too long/i.test(requestError.message);
      showError(t(cryptoFailure ? "errorCrypto" : "errorGeneric"));
    } finally {
      setLoading(false);
    }
  };

  return {
    username,
    password,
    passwordVisible,
    loading,
    providers,
    setupRequired: status?.setupRequired === true,
    error,
    setUsername,
    setPassword,
    togglePasswordVisibility,
    submit,
  };
}
