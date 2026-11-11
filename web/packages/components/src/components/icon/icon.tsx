import { Icon as NetworkIcon } from "@iconify/react";
import { Icon as OfflineIcon, type IconProps as IconifyProps } from "@iconify/react/offline";
import { cn } from "../../utils/cn";
import { registerBuiltinIcons } from "./builtin-icons";
import { registerCloudreveIcons } from "./cloudreve-icons";
import { isRegisteredIcon } from "./icon-registry";
import styles from "./icon.module.css";

registerBuiltinIcons();
registerCloudreveIcons();

export { registerCollection as registerIconCollection, registerIcon } from "./icon-registry";

export interface IconProps extends Omit<IconifyProps, "icon" | "color"> {
  /** Iconify identifier, for example "mdi:home-outline". */
  name: string;
  size?: number | string;
  /** Defaults to currentColor so buttons and links control the color. */
  color?: string;
  label?: string;
  spin?: boolean;
}

/**
 * Iconify-backed icon primitive. Registered icons (the bundled MDI subset plus
 * anything added through registerIcon/registerIconCollection) render offline
 * and synchronously; other names use Iconify's API in the browser.
 */
export function Icon({
  name,
  size,
  color = "currentColor",
  label,
  spin = false,
  className,
  style,
  ...props
}: IconProps) {
  const dimension = typeof size === "number" ? `${size}px` : size;
  const iconProps = {
    icon: name,
    width: dimension,
    height: dimension,
    color,
    inline: true,
    className: cn(styles.icon, spin && styles.spin, className),
    style,
    role: label ? "img" : undefined,
    "aria-label": label,
    "aria-hidden": label ? undefined : true,
    ...props,
  };

  return isRegisteredIcon(name) ? <OfflineIcon {...iconProps} /> : <NetworkIcon {...iconProps} />;
}
