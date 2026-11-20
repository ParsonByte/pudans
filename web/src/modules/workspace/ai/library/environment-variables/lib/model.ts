import type { EnvironmentVariableItem } from "./api";

export function environmentVariableDisplayName(variable: EnvironmentVariableItem): string {
  return variable.spec.name || variable.metadata.name;
}

export function environmentVariableConfigured(variable: EnvironmentVariableItem): boolean {
  return variable.spec.configured === true;
}
