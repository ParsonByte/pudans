import { type NextRequest, NextResponse } from "next/server";

import { requireAuthentication } from "@/routes/_authenticated/route";

export async function middleware(request: NextRequest) {
    if (await requireAuthentication(request)) {
        return NextResponse.next();
    }

    const signInUrl = new URL("/sign-in", request.url);
    signInUrl.searchParams.set("redirect", `${request.nextUrl.pathname}${request.nextUrl.search}`);

    return NextResponse.redirect(signInUrl);
}

export const config = {
    matcher: ["/((?!$|sign-in|_next|favicon.ico|brand/|login-bg.mp4|apis|login).*)"],
};
