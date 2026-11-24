import { type NextRequest, NextResponse } from "next/server";

import { readSetupRequired } from "@/modules/_setup/composables/_setup-guard";
import {
    INDEX_BASE_PATH,
    INDEX_LEGACY_PAGE_PARAM,
    INDEX_NAV_GROUP,
    INDEX_SESSION_PARAM,
    indexPageHref,
    isIndexPageKey,
    legacyIndexPageFromPathname,
} from "@/routes/(_workspace)/(_index)/paths";
import { requireAuthentication, resolveDefaultWorkspacePath } from "@/routes/_authenticated/route";

const SETUP_PATH = "/setup";
const SIGN_IN_PATH = "/sign-in";

function legacyIndexRedirect(request: NextRequest): NextResponse | undefined {
    const navigationPage = request.nextUrl.searchParams.get(INDEX_NAV_GROUP);
    const queryPage = request.nextUrl.searchParams.get(INDEX_LEGACY_PAGE_PARAM);
    let page = legacyIndexPageFromPathname(request.nextUrl.pathname);

    if (
        !page &&
        request.nextUrl.pathname === INDEX_BASE_PATH &&
        !isIndexPageKey(navigationPage ?? "") &&
        queryPage &&
        isIndexPageKey(queryPage)
    ) {
        page = queryPage;
    }

    if (!page) {
        return undefined;
    }

    const sessionIds = request.nextUrl.searchParams.getAll(INDEX_SESSION_PARAM);

    return NextResponse.redirect(new URL(indexPageHref(page, sessionIds), request.url));
}

export async function middleware(request: NextRequest) {
    const { pathname } = request.nextUrl;

    if (pathname === "/" || pathname === SIGN_IN_PATH || pathname === SETUP_PATH) {
        const setupRequired = await readSetupRequired();

        if (setupRequired === true) {
            return pathname === SETUP_PATH
                ? NextResponse.next()
                : NextResponse.redirect(new URL(SETUP_PATH, request.url));
        }

        if (setupRequired === false && pathname === SETUP_PATH) {
            return NextResponse.redirect(new URL(SIGN_IN_PATH, request.url));
        }

        if (await requireAuthentication(request)) {
            const workspacePath = await resolveDefaultWorkspacePath(request);

            if (workspacePath) {
                return NextResponse.redirect(new URL(workspacePath, request.url));
            }
        }

        if (pathname === "/") {
            return NextResponse.redirect(new URL(SIGN_IN_PATH, request.url));
        }

        return NextResponse.next();
    }

    if (await requireAuthentication(request)) {
        const legacyRedirect = legacyIndexRedirect(request);

        if (legacyRedirect) {
            return legacyRedirect;
        }

        return NextResponse.next();
    }

    const signInUrl = new URL(SIGN_IN_PATH, request.url);
    signInUrl.searchParams.set("redirect", `${request.nextUrl.pathname}${request.nextUrl.search}`);

    return NextResponse.redirect(signInUrl);
}

export const config = {
    matcher: ["/((?!_next|favicon.ico|brand/|login-bg.mp4|apis|login).*)"],
};
