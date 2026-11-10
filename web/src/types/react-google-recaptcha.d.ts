declare module "react-google-recaptcha" {
  import type { ComponentType } from "react";

  export interface ReCAPTCHAProps {
    sitekey: string;
    theme?: "light" | "dark";
    size?: "compact" | "normal" | "invisible";
    badge?: "bottomright" | "bottomleft" | "inline";
    hl?: string;
    tabindex?: number;
    onChange?: (token: string | null) => void;
    onExpired?: () => void;
    onErrored?: () => void;
    onLoad?: () => void;
    grecaptcha?: unknown;
  }

  const ReCAPTCHA: ComponentType<ReCAPTCHAProps>;
  export default ReCAPTCHA;
}
