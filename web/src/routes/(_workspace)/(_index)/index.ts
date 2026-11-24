import type { ComponentType } from "react";

import { AiToolsRoute } from "./ai-tools/route";
import { AssetsRoute } from "./assets/route";
import { ConversationRoute } from "./conversation/route";
import { CreateRoute } from "./create/route";
import { DansRoute } from "./dans/route";
import { DeepUnderstandingRoute } from "./deep-understanding/route";
import { DreamSpaceRoute } from "./dream-space/route";
import { ExploreRoute } from "./explore/route";
import { INDEX_PAGE_KEYS, type IndexPageKey } from "./paths";

export const INDEX_PAGE_VIEWS: Record<IndexPageKey, ComponentType> = {
    [INDEX_PAGE_KEYS.create]: CreateRoute,
    [INDEX_PAGE_KEYS.explore]: ExploreRoute,
    [INDEX_PAGE_KEYS.dans]: DansRoute,
    [INDEX_PAGE_KEYS.aiTools]: AiToolsRoute,
    [INDEX_PAGE_KEYS.assets]: AssetsRoute,
    [INDEX_PAGE_KEYS.deepUnderstanding]: DeepUnderstandingRoute,
    [INDEX_PAGE_KEYS.dreamSpace]: DreamSpaceRoute,
    [INDEX_PAGE_KEYS.conversation]: ConversationRoute,
};
