const USER_MENU_GROUP_KEY = "identity";
const USER_MENU_KEY = "users";

export function userListPath(workspace: string): string {
    return `/workspace/${encodeURIComponent(workspace)}?${USER_MENU_GROUP_KEY}=${USER_MENU_KEY}`;
}

export function userDetailPath(workspace: string, name: string): string {
    return `/workspace/${encodeURIComponent(workspace)}/${USER_MENU_GROUP_KEY}/${USER_MENU_KEY}/${encodeURIComponent(name)}`;
}
