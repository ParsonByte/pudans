import { Gem, type LucideIcon, Sparkles, Zap } from "lucide-react";

import type { ChatAttachmentRef, ChatMode, ChatRouteGroup, ChatSlashCommand } from "./chat-api";

export type ChatFunction = {
  label: string;
  icon: LucideIcon;
  mode: string;
};

export type ChatRequestBody = {
  prompt: string;
  attachments: ChatAttachmentRef[];
  command?: string;
  commandInput?: string;
};

export type ChatCommandItem =
  | {
      kind: "mode";
      value: string;
      label: string;
      mode: string;
      icon: LucideIcon;
    }
  | {
      kind: "command";
      value: string;
      label: string;
      command: string;
      description?: string;
      argumentHint?: string;
    };

const MODE_ICONS: Record<string, LucideIcon> = {
  fast: Zap,
  pro: Gem,
};

const SLASH_COMMAND_PATTERN = /^\/([a-z0-9][a-z0-9-]*)(?:\s+([\s\S]*))?$/;

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

export function chatRouteGroupParentMode(modeName?: string): "FAST" | "PRO" | undefined {
  const normalized = modeName?.toLowerCase();
  if (normalized === "fast") {
    return "FAST";
  }
  if (normalized === "pro") {
    return "PRO";
  }
  return undefined;
}

export function chatRouteGroupsForMode(
  groups: ChatRouteGroup[],
  modeName?: string,
): ChatRouteGroup[] {
  const parentMode = chatRouteGroupParentMode(modeName);
  return parentMode ? groups.filter((group) => group.parentMode === parentMode) : groups;
}

export function parseChatCommand(
  prompt: string,
  commands: ChatSlashCommand[],
): { command?: string; commandInput?: string } {
  const match = SLASH_COMMAND_PATTERN.exec(prompt.trim());
  if (!match) {
    return {};
  }
  const name = match[1];
  if (!commands.some((command) => command.name === name)) {
    return {};
  }
  return { command: name, commandInput: match[2]?.trim() || undefined };
}
