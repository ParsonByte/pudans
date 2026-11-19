import { Gem, type LucideIcon, Sparkles, Zap } from "lucide-react";

import type { ChatMode } from "./chat-api";

export type ChatFunction = {
  label: string;
  icon: LucideIcon;
  mode: string;
};

const MODE_ICONS: Record<string, LucideIcon> = {
  fast: Zap,
  pro: Gem,
};

export function chatModeIcon(name: string): LucideIcon {
  return MODE_ICONS[name] ?? Sparkles;
}

export function chatModeLabel(mode: ChatMode): string {
  return (mode.displayName || mode.name).replace(/模式$/, "");
}

export function chatFunctionsForModes(modes: ChatMode[]): ChatFunction[] {
  return modes.map((mode) => ({
    label: chatModeLabel(mode),
    icon: chatModeIcon(mode.name),
    mode: mode.name,
  }));
}
