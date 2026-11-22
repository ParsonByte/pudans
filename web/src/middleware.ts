import { type NextRequest, NextResponse } from "next/server";

import { readSetupRequired } from "@/modules/_setup/composables/_setup-guard";
import { requireAuthentication, resolveDefaultWorkspacePath } from "@/routes/_authenticated/route";

const SETUP_PATH = "/setup";
const SIGN_IN_PATH = "/sign-in";

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
        return NextResponse.next();
    }

    const signInUrl = new URL(SIGN_IN_PATH, request.url);
    signInUrl.searchParams.set("redirect", `${request.nextUrl.pathname}${request.nextUrl.search}`);

    return NextResponse.redirect(signInUrl);
}

export const config = {
    matcher: ["/((?!_next|favicon.ico|brand/|login-bg.mp4|apis|login).*)"],
};
