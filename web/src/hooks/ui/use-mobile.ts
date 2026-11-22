"use client";

import { useMediaQuery } from "@/hooks/ui/use-media-query";

const MOBILE_QUERY = "(max-width: 767px)";

export function useMobile() {
    return useMediaQuery(MOBILE_QUERY);
}
