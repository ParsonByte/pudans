"use client";

import { createElement } from "react";

import { INDEX_PAGE_VIEWS } from "@/routes/(_workspace)/(_index)";

import { useIndexRoute } from "./composables/_index-route";

export function IndexView() {
    const page = useIndexRoute();

    return createElement(INDEX_PAGE_VIEWS[page]);
}
