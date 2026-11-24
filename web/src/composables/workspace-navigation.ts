export const HOME_WORKSPACE_KEY = "index";

type WorkspaceIdentity = {
    id?: string;
    slug?: string;
};

export function workspaceKey(workspace: WorkspaceIdentity): string | undefined {
    return workspace.slug ?? workspace.id;
}

export function workspaceBasePath(workspace: WorkspaceIdentity): string | undefined {
    const key = workspaceKey(workspace);

    return key ? `/workspace/${encodeURIComponent(key)}` : undefined;
}
