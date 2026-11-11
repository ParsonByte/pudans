import { useEffect, useRef, useState, type CSSProperties } from "react";
import { cn } from "../../utils/cn";
import styles from "./captcha.module.css";

export type CaptchaProvider = "turnstile" | "recaptcha";

export interface CaptchaProps {
  provider: CaptchaProvider;
  siteKey: string;
  onVerify: (token: string | null) => void;
  onError?: (message: string) => void;
  theme?: "light" | "dark" | "auto";
  language?: string;
  className?: string;
  style?: CSSProperties;
}

interface TurnstileApi {
  render: (container: HTMLElement, options: Record<string, unknown>) => string;
  remove: (widgetId: string) => void;
}

interface RecaptchaApi {
  render: (container: HTMLElement, options: Record<string, unknown>) => number;
  reset: (widgetId?: number) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
    grecaptcha?: RecaptchaApi;
  }
}

const SCRIPT_SRC: Record<CaptchaProvider, string> = {
  turnstile: "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit",
  recaptcha: "https://www.google.com/recaptcha/api.js?render=explicit",
};

const scriptPromises = new Map<string, Promise<void>>();

function loadScript(src: string): Promise<void> {
  const existing = scriptPromises.get(src);
  if (existing) {
    return existing;
  }
  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load captcha script"));
    document.head.appendChild(script);
  });
  scriptPromises.set(src, promise);
  return promise;
}

function waitForApi(provider: CaptchaProvider): Promise<void> {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const check = (): void => {
      const api =
        provider === "turnstile"
          ? window.turnstile
          : window.grecaptcha?.render
            ? window.grecaptcha
            : undefined;
      if (api) {
        resolve();
        return;
      }
      if (Date.now() - started > 10000) {
        reject(new Error("Captcha API timed out"));
        return;
      }
      window.setTimeout(check, 50);
    };
    check();
  });
}

/**
 * Captcha widget wrapper consolidated from the legacy five Common/Captcha
 * files. Loads the provider script on demand, renders explicitly and reports
 * tokens through onVerify. No provider SDK is bundled.
 */
export function Captcha({
  provider,
  siteKey,
  onVerify,
  onError,
  theme = "auto",
  language,
  className,
  style,
}: CaptchaProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetRef = useRef<string | number | null>(null);
  const onVerifyRef = useRef(onVerify);
  const onErrorRef = useRef(onError);
  const [failed, setFailed] = useState(false);
  onVerifyRef.current = onVerify;
  onErrorRef.current = onError;

  useEffect(() => {
    let cancelled = false;
    const container = containerRef.current;
    if (!container || !siteKey) {
      return;
    }

    const render = async (): Promise<void> => {
      try {
        await loadScript(SCRIPT_SRC[provider]);
        await waitForApi(provider);
        if (cancelled || !containerRef.current) {
          return;
        }
        containerRef.current.innerHTML = "";

        if (provider === "turnstile") {
          widgetRef.current = window.turnstile!.render(containerRef.current, {
            sitekey: siteKey,
            theme,
            language,
            callback: (token: string) => onVerifyRef.current(token),
            "error-callback": () => onVerifyRef.current(null),
            "expired-callback": () => onVerifyRef.current(null),
          });
          return;
        }

        widgetRef.current = window.grecaptcha!.render(containerRef.current, {
          sitekey: siteKey,
          theme: theme === "auto" ? "light" : theme,
          hl: language,
          callback: (token: string) => onVerifyRef.current(token),
          "error-callback": () => onVerifyRef.current(null),
          "expired-callback": () => onVerifyRef.current(null),
        });
      } catch (error) {
        if (cancelled) {
          return;
        }
        setFailed(true);
        onErrorRef.current?.(error instanceof Error ? error.message : "Captcha failed to load");
      }
    };

    void render();

    return () => {
      cancelled = true;
      if (widgetRef.current !== null) {
        if (provider === "turnstile") {
          try {
            window.turnstile?.remove(widgetRef.current as string);
          } catch {
            // widget already removed
          }
        } else {
          try {
            window.grecaptcha?.reset(widgetRef.current as number);
          } catch {
            // widget already removed
          }
        }
        widgetRef.current = null;
      }
    };
  }, [provider, siteKey, theme, language]);

  if (failed) {
    return (
      <div className={cn(styles.error, className)} role="alert">
        Captcha could not be loaded. Check your network connection and try again.
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className={cn(styles.root, className)}
      style={style}
      data-provider={provider}
    />
  );
}
