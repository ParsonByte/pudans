let loggingOut = false;

/** Set before the logout request so mounted console/uc apps do not race it. */
export function beginLogout(): void {
  loggingOut = true;
}

export function endLogout(): void {
  loggingOut = false;
}

export function isLoggingOut(): boolean {
  return loggingOut;
}
