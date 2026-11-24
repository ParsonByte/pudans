const OIDC_BIND_STORAGE_KEY = "pudans.uc.oidc-bind";

export type PendingOidcBind = {
    redirectUri: string;
    registrationId: string;
    state: string;
};

export function readPendingOidcBind(): PendingOidcBind | undefined {
    try {
        const value = window.sessionStorage.getItem(OIDC_BIND_STORAGE_KEY);
        const parsed = value ? (JSON.parse(value) as Partial<PendingOidcBind>) : undefined;

        if (!parsed?.redirectUri || !parsed.registrationId || !parsed.state) {
            return undefined;
        }

        return {
            redirectUri: parsed.redirectUri,
            registrationId: parsed.registrationId,
            state: parsed.state,
        };
    } catch {
        return undefined;
    }
}

export function savePendingOidcBind(pending: PendingOidcBind): void {
    window.sessionStorage.setItem(OIDC_BIND_STORAGE_KEY, JSON.stringify(pending));
}

export function clearPendingOidcBind(): void {
    window.sessionStorage.removeItem(OIDC_BIND_STORAGE_KEY);
}
