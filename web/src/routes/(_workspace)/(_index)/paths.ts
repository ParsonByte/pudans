import { HOME_WORKSPACE_KEY } from "@/composables/workspace-navigation";

export const INDEX_NAV_GROUP = "index";
export const INDEX_LEGACY_PAGE_PARAM = "page";
export const INDEX_SESSION_PARAM = "session";

export const INDEX_PAGE_KEYS = {
    create: "create",
    explore: "explore",
    dans: "dans",
    aiTools: "ai-tools",
    assets: "assets",
    deepUnderstanding: "deep-understanding",
    conversation: "conversation",
} as const;

export type IndexPageKey = (typeof INDEX_PAGE_KEYS)[keyof typeof INDEX_PAGE_KEYS];

export const INDEX_BASE_PATH = `/workspace/${HOME_WORKSPACE_KEY}`;

const INDEX_PATH_PREFIX = `${INDEX_BASE_PATH}/`;
const INDEX_PAGE_VALUES = new Set<string>(Object.values(INDEX_PAGE_KEYS));
const INDEX_DETAIL_PATH_KEYS = {
    activity: "activity",
    explore: "explore",
    work: "work",
} as const;
const INDEX_DETAIL_PAGES = new Map<string, IndexPageKey>([
    [INDEX_DETAIL_PATH_KEYS.activity, INDEX_PAGE_KEYS.create],
    [INDEX_DETAIL_PATH_KEYS.explore, INDEX_PAGE_KEYS.explore],
    [INDEX_DETAIL_PATH_KEYS.work, INDEX_PAGE_KEYS.create],
]);

export function isIndexPageKey(value: string): value is IndexPageKey {
    return INDEX_PAGE_VALUES.has(value);
}

function decodePathSegment(value: string): string {
    try {
        return decodeURIComponent(value);
    } catch {
        return value;
    }
}

function indexPathSegments(pathname: string | null | undefined): string[] {
    if (!pathname?.startsWith(INDEX_PATH_PREFIX)) {
        return [];
    }

    return pathname.slice(INDEX_PATH_PREFIX.length).split("/").filter(Boolean).map(decodePathSegment);
}

export function legacyIndexPageFromPathname(pathname: string | null | undefined): IndexPageKey | undefined {
    const segments = indexPathSegments(pathname);

    if (segments.length !== 1) {
        return undefined;
    }

    const page = segments[0];

    return isIndexPageKey(page) ? page : undefined;
}

export function resolveIndexPage(
    pathname: string | null | undefined,
    navigationPage?: string | null,
    legacyPage?: string | null,
): IndexPageKey {
    const segments = indexPathSegments(pathname);

    if (segments.length === 1 && isIndexPageKey(segments[0])) {
        return segments[0];
    }

    const detailPage = segments.length === 2 ? INDEX_DETAIL_PAGES.get(segments[0]) : undefined;

    if (detailPage) {
        return detailPage;
    }

    if (navigationPage && isIndexPageKey(navigationPage)) {
        return navigationPage;
    }

    return legacyPage && isIndexPageKey(legacyPage) ? legacyPage : INDEX_PAGE_KEYS.create;
}

export function indexPageHref(page: IndexPageKey, sessionIds: readonly string[] = []): string {
    const params = new URLSearchParams();

    params.set(INDEX_NAV_GROUP, page);

    if (page === INDEX_PAGE_KEYS.conversation) {
        for (const sessionId of sessionIds) {
            params.append(INDEX_SESSION_PARAM, sessionId);
        }
    }

    const search = params.toString();

    return `${INDEX_BASE_PATH}?${search}`;
}

export function indexWorkHref(name: string): string {
    return `${INDEX_BASE_PATH}/${INDEX_DETAIL_PATH_KEYS.work}/${encodeURIComponent(name)}`;
}

export function indexExploreWorkHref(name: string): string {
    return `${INDEX_BASE_PATH}/${INDEX_DETAIL_PATH_KEYS.explore}/${encodeURIComponent(name)}`;
}

export function indexActivityHref(name: string): string {
    return `${INDEX_BASE_PATH}/${INDEX_DETAIL_PATH_KEYS.activity}/${encodeURIComponent(name)}`;
}
