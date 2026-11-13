export interface SessionView {
    'authenticated': boolean;
    'username'?: string;
    'displayName'?: string;
    'avatar'?: string;
    'redirectUrl': string;
    'consoleAccessDisallowed'?: boolean;
    'uiPermissions'?: Array<string>;
}
