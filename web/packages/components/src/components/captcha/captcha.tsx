import {
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type Ref,
} from "react";
import {
  Box,
  InputAdornment,
  Skeleton,
  TextField,
  type SxProps,
  type Theme,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";

export type CaptchaProvider = "turnstile" | "recaptcha" | "cap" | "image" | "default";

export interface CaptchaProps {
  /** Legacy CaptchaType switch: recaptcha / turnstile / cap / image (default captcha). */
  provider: CaptchaProvider;
  siteKey?: string;
  onVerify?: (token: string | null) => void;
  onError?: (message: string) => void;
  theme?: "light" | "dark" | "auto";
  language?: string;
  fullWidth?: boolean;
  /** Cap (cap-widget) configuration — legacy CapCaptcha props. */
  capInstanceUrl?: string;
  capSiteKey?: string;
  capAssetServer?: "instance" | "unpkg" | "jsdelivr";
  /** Image captcha (legacy DefaultCaptcha) props; the app supplies the image. */
  imageSrc?: string;
  label?: string;
  value?: string;
  onChange?: (value: string) => void;
  onRefresh?: () => void;
  refreshTitle?: string;
  inputRef?: Ref<HTMLInputElement>;
  className?: string;
  style?: CSSProperties;
  sx?: SxProps<Theme>;
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

const SCRIPT_SRC: Record<"turnstile" | "recaptcha", string> = {
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
    script.onerror = () => reject(new Error(`Failed to load ${src}`));
    document.head.appendChild(script);
  });
  scriptPromises.set(src, promise);
  return promise;
}

function waitForApi(provider: "turnstile" | "recaptcha"): Promise<void> {
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

function capAssets(
  server: "instance" | "unpkg" | "jsdelivr",
  instanceUrl: string | undefined,
): { widget: string; wasm: string } {
  if (server === "instance" && instanceUrl) {
    const clean = instanceUrl.replace(/\/$/, "");
    return { widget: `${clean}/assets/widget.js`, wasm: `${clean}/assets/cap_wasm_bg.wasm` };
  }
  if (server === "unpkg") {
    return {
      widget: "https://unpkg.com/@cap.js/widget",
      wasm: "https://unpkg.com/@cap.js/wasm/browser/cap_wasm_bg.wasm",
    };
  }
  return {
    widget: "https://cdn.jsdelivr.net/npm/@cap.js/widget",
    wasm: "https://cdn.jsdelivr.net/npm/@cap.js/wasm/browser/cap_wasm_bg.wasm",
  };
}

/**
 * Legacy Common/Captcha/*: one dispatcher component covering the four provider
 * looks. reCAPTCHA / Turnstile mount inside a centred Box (legacy
 * sx={{ textAlign: "center" }}); Cap renders the cap-widget web component with
 * the legacy theme CSS variables (radius 12, paper/divider/primary colours,
 * 16px padding, 12px gap); the image variant reproduces DefaultCaptcha — an
 * outlined TextField with a 192x48 captcha image (or Skeleton) as endAdornment,
 * 4px right input padding and 12px image radius.
 */
export function Captcha({
  provider,
  siteKey,
  onVerify,
  onError,
  theme: themeProp = "auto",
  language,
  fullWidth = false,
  capInstanceUrl,
  capSiteKey,
  capAssetServer = "jsdelivr",
  imageSrc,
  label,
  value,
  onChange,
  onRefresh,
  refreshTitle,
  inputRef,
  className,
  style,
  sx,
}: CaptchaProps) {
  const theme = useTheme();
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetRef = useRef<string | number | null>(null);
  const onVerifyRef = useRef(onVerify);
  const onErrorRef = useRef(onError);
  const [failed, setFailed] = useState(false);
  onVerifyRef.current = onVerify;
  onErrorRef.current = onError;

  const mode = themeProp === "auto" ? theme.palette.mode : themeProp;
  const effectiveProvider: "turnstile" | "recaptcha" | "cap" | "image" =
    provider === "default" ? "image" : provider;

  useEffect(() => {
    if (effectiveProvider === "image" || effectiveProvider === "cap") {
      return;
    }
    let cancelled = false;
    const container = containerRef.current;
    if (!container || !siteKey) {
      return;
    }
    const render = async (): Promise<void> => {
      try {
        await loadScript(SCRIPT_SRC[effectiveProvider]);
        await waitForApi(effectiveProvider);
        if (cancelled || !containerRef.current) {
          return;
        }
        containerRef.current.innerHTML = "";
        if (effectiveProvider === "turnstile") {
          widgetRef.current = window.turnstile!.render(containerRef.current, {
            sitekey: siteKey,
            size: "flexible",
            theme: mode,
            language,
            callback: (token: string) => onVerifyRef.current?.(token),
            "error-callback": () => onVerifyRef.current?.(null),
            "expired-callback": () => onVerifyRef.current?.(null),
          });
          return;
        }
        widgetRef.current = window.grecaptcha!.render(containerRef.current, {
          sitekey: siteKey,
          theme: mode,
          hl: language,
          callback: (token: string) => onVerifyRef.current?.(token),
          "error-callback": () => onVerifyRef.current?.(null),
          "expired-callback": () => onVerifyRef.current?.(null),
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
        if (effectiveProvider === "turnstile") {
          try {
            window.turnstile?.remove(widgetRef.current as string);
          } catch {
            // already removed
          }
        } else {
          try {
            window.grecaptcha?.reset(widgetRef.current as number);
          } catch {
            // already removed
          }
        }
        widgetRef.current = null;
      }
    };
  }, [effectiveProvider, siteKey, mode, language]);

  useEffect(() => {
    if (effectiveProvider !== "cap" || !capInstanceUrl || !capSiteKey) {
      return;
    }
    let cancelled = false;
    const assets = capAssets(capAssetServer, capInstanceUrl);
    (window as unknown as { CAP_CUSTOM_WASM_URL?: string }).CAP_CUSTOM_WASM_URL = assets.wasm;
    const scriptId = "cap-widget-script";
    const createWidget = (): void => {
      if (cancelled || !containerRef.current) {
        return;
      }
      containerRef.current.innerHTML = "";
      const widget = document.createElement("cap-widget") as HTMLElement;
      const endpoint = `${capInstanceUrl.replace(/\/$/, "")}/${capSiteKey}/`;
      widget.setAttribute("data-cap-api-endpoint", endpoint);
      widget.id = "cap-widget";
      widget.addEventListener("solve", (event: Event) => {
        const token = (event as CustomEvent<{ token?: string }>).detail?.token;
        if (token) {
          onVerifyRef.current?.(token);
        }
      });
      if (fullWidth) {
        widget.style.width = "100%";
        widget.style.display = "block";
        widget.style.boxSizing = "border-box";
      }
      containerRef.current.appendChild(widget);
    };
    const existing = document.getElementById(scriptId) as HTMLScriptElement | null;
    if (existing) {
      createWidget();
    } else {
      const script = document.createElement("script");
      script.id = scriptId;
      script.src = assets.widget;
      script.async = true;
      script.onload = createWidget;
      document.head.appendChild(script);
    }
    return () => {
      cancelled = true;
      if (containerRef.current) {
        containerRef.current.innerHTML = "";
      }
    };
  }, [effectiveProvider, capInstanceUrl, capSiteKey, capAssetServer, fullWidth]);

  if (effectiveProvider === "image") {
    return (
      <TextField
        className={className}
        style={style}
        sx={[{ "& .MuiOutlinedInput-root": { pr: 0.5 } }, ...(Array.isArray(sx) ? sx : sx ? [sx] : [])]}
        variant="outlined"
        label={label}
        value={value ?? ""}
        onChange={(event) => onChange?.(event.target.value)}
        inputRef={inputRef}
        fullWidth={fullWidth}
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <Box
                  title={refreshTitle}
                  onClick={onRefresh}
                  sx={{ cursor: "pointer", height: theme.spacing(6), display: "flex", alignItems: "center" }}
                >
                  {imageSrc ? (
                    <Box
                      component="img"
                      src={imageSrc}
                      alt="captcha"
                      sx={{ borderRadius: 1.5, height: theme.spacing(6) }}
                    />
                  ) : (
                    <Skeleton
                      animation="wave"
                      variant="rounded"
                      width={theme.spacing(24)}
                      height={theme.spacing(6)}
                      sx={{ borderRadius: 1.5 }}
                    />
                  )}
                </Box>
              </InputAdornment>
            ),
          },
        }}
      />
    );
  }

  if (effectiveProvider === "cap") {
    if (!capInstanceUrl || !capSiteKey) {
      return null;
    }
    return (
      <Box
        className={className}
        style={style}
        sx={[
          fullWidth ? { width: "100%" } : {},
          {
            "& cap-widget": {
              "--cap-border-radius": `${theme.shape.borderRadius}px`,
              "--cap-background": theme.palette.background.paper,
              "--cap-border-color": theme.palette.divider,
              "--cap-color": theme.palette.text.primary,
              "--cap-widget-height": fullWidth ? theme.spacing(7) : "auto",
              "--cap-widget-padding": theme.spacing(2),
              "--cap-gap": theme.spacing(1.5),
              "--cap-checkbox-size": theme.spacing(2.5),
              "--cap-checkbox-border-radius": `${Number(theme.shape.borderRadius) / 3}px`,
              "--cap-checkbox-background": theme.palette.action.hover,
              "--cap-checkbox-border": `1px solid ${theme.palette.divider}`,
              "--cap-font": String(theme.typography.fontFamily ?? "Roboto, sans-serif"),
              "--cap-spinner-color": theme.palette.primary.main,
              "--cap-spinner-background-color": theme.palette.action.hover,
              "--cap-credits-font-size": String(theme.typography.caption.fontSize ?? "12px"),
              "--cap-opacity-hover": "0.7",
            },
          },
          ...(Array.isArray(sx) ? sx : sx ? [sx] : []),
        ]}
      >
        <div ref={containerRef} />
      </Box>
    );
  }

  if (failed) {
    return null;
  }

  return (
    <Box
      className={className}
      style={style}
      sx={[{ textAlign: "center" }, ...(Array.isArray(sx) ? sx : sx ? [sx] : [])]}
    >
      <Box
        ref={containerRef}
        data-provider={effectiveProvider}
        sx={{ display: effectiveProvider === "recaptcha" ? "inline-block" : "flex", justifyContent: "center" }}
      />
    </Box>
  );
}
