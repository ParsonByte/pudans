import type { SlashCommandItem } from "./api";

export function slashCommandLabel(command: SlashCommandItem): string {
  return `/${command.spec.name}`;
}

export function slashCommandDisplayName(command: SlashCommandItem): string {
  return command.spec.name || command.metadata.name;
}

export function slashCommandArgumentHint(command: SlashCommandItem): string {
  return command.spec.argumentHint || "—";
}
