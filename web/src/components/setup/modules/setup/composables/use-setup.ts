import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router-dom";
import { isPudansApiError, setupSystem } from "@pudans/api-client";
import { sanitizeRedirectUri } from "../../../../../hooks/use-auth-navigation";
import { useSystem } from "../../../../../session/use-system";

const USERNAME_PATTERN = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?(\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*$/;
const PASSWORD_PATTERN = /^[A-Za-z0-9!@#$%^&*.?]+$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export interface SetupFormValues {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  siteTitle: string;
  siteUrl: string;
}

export function useSetup() {
  const { t } = useTranslation("setup");
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { refresh } = useSystem();
  const [values, setValues] = useState<SetupFormValues>({
    username: "",
    email: "",
    password: "",
    confirmPassword: "",
    siteTitle: "",
    siteUrl: "",
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setField = <K extends keyof SetupFormValues>(key: K, value: SetupFormValues[K]): void => {
    setValues((current) => ({ ...current, [key]: value }));
  };

  const validate = (): string | null => {
    const username = values.username.trim();
    if (!username || !values.password || !values.siteTitle.trim() || !values.siteUrl.trim()) {
      return t("errorRequired");
    }
    if (!USERNAME_PATTERN.test(username) || username.length < 4 || username.length > 63) {
      return t("usernameInvalid");
    }
    if (values.email.trim() && !EMAIL_PATTERN.test(values.email.trim())) {
      return t("emailInvalid");
    }
    if (values.password.length < 5 || values.password.length > 257 || !PASSWORD_PATTERN.test(values.password)) {
      return t("passwordMin");
    }
    if (values.password !== values.confirmPassword) {
      return t("passwordMismatch");
    }
    try {
      const url = new URL(values.siteUrl.trim());
      if (!["http:", "https:"].includes(url.protocol)) {
        return t("urlInvalid");
      }
    } catch {
      return t("urlInvalid");
    }
    return null;
  };

  const submit = async (event: FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await setupSystem({
        username: values.username.trim(),
        password: values.password,
        email: values.email.trim() || undefined,
        siteTitle: values.siteTitle.trim(),
        siteUrl: values.siteUrl.trim(),
      });
      await refresh();
      const redirectUri = sanitizeRedirectUri(searchParams.get("redirect_uri"));
      navigate(
        redirectUri ? `/auth/?redirect_uri=${encodeURIComponent(redirectUri)}` : "/auth/",
        { replace: true },
      );
    } catch (requestError) {
      setError(
        isPudansApiError(requestError) && requestError.message
          ? requestError.message
          : t("errorGeneric"),
      );
    } finally {
      setLoading(false);
    }
  };

  return { values, setField, loading, error, submit };
}
