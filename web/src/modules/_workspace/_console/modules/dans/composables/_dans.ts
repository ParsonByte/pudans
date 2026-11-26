import { axiosInstance } from "@pudans-dev/api-client";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

type CollectionResponse<T> = T[] | { items?: T[] };

type PageResponse<T> = {
    items?: T[];
    nextCursor?: string;
};

export type AiChannel = {
    baseUrl?: string;
    credentialConfigured?: boolean;
    credentialStatus?: Record<string, unknown>;
    id: string;
    maxConcurrency?: number;
    protocol?: string;
    provider?: string;
    rateLimit?: number;
    status?: string;
    timeoutMs?: number;
    updatedAt?: string;
    weight?: number;
    workspaceId?: string;
};

export type AiModel = {
    capabilities?: Record<string, unknown>;
    contextWindow?: number;
    description?: string;
    displayName?: string;
    id: string;
    logicalName?: string;
    maxOutputTokens?: number;
    status?: string;
    updatedAt?: string;
};

export type AiChannelModel = {
    channelId?: string;
    enabled?: boolean;
    id: string;
    modelId?: string;
    priority?: number;
    upstreamModelKey?: string;
    updatedAt?: string;
    weight?: number;
};

export type AiDefaultModel = {
    capability?: string;
    id: string;
    modelId?: string;
    updatedAt?: string;
    workspaceId?: string;
};

export type AiToolDescriptor = {
    description?: string;
    enabled?: boolean;
    key: string;
    label?: string;
};

export type AiPriceRule = {
    baseCredits?: number;
    effectiveFrom?: string;
    effectiveTo?: string;
    id: string;
    mcpToolKey?: string;
    minimumCredits?: number;
    modelId?: string;
    pricingMode?: string;
    quotaBucket?: string;
    targetKey?: string;
    targetType?: string;
    unit?: string;
    unitCredits?: number;
};

export type AiQuotaPlan = {
    displayName?: string;
    fiveHourCredits?: number;
    id: string;
    name?: string;
    status?: string;
    weeklyCredits?: number;
};

export type AiQuotaAssignment = {
    effectiveFrom?: string;
    effectiveTo?: string;
    id: string;
    planId?: string;
    status?: string;
    subjectId?: string;
    subjectType?: string;
};

export type CatalogEntry = {
    catalogKind?: string;
    description?: string;
    enabled?: boolean;
    entryKey?: string;
    id: string;
    label?: string;
    sortOrder?: number;
    validFrom?: string;
    validTo?: string;
};

export type AgentSession = {
    createdAt?: string;
    id: string;
    lastMessageAt?: string;
    ownerId?: string;
    status?: string;
    title?: string;
    updatedAt?: string;
    workspaceId?: string;
};

export type AgentSessionSummary = {
    active?: number;
    latestMessageAt?: string;
    total?: number;
};

export type Sandbox = {
    createdAt?: string;
    errorCode?: string;
    errorMessage?: string;
    expiresAt?: string;
    id: string;
    ownerId?: string;
    provider?: string;
    region?: string;
    startedAt?: string;
    status?: string;
    template?: string;
    workspaceId?: string;
};

export type MemoryPoolItem = {
    confidence?: number;
    createdAt?: string;
    generalizedContent?: string;
    id: string;
    layer?: string;
    memoryType?: string;
    reviewedAt?: string;
    safetyClass?: string;
    sourceOwnerCount?: number;
    sourceWorkspaceCount?: number;
    status?: string;
    summary?: string;
    supportCount?: number;
    title?: string;
    updatedAt?: string;
};

export type MemoryJob = {
    createdAt?: string;
    errorCode?: string;
    jobType?: string;
    processedItems?: number;
    scopeType?: string;
    status?: string;
    totalItems?: number;
};

export type MemoryVectorSpace = {
    dimension?: number;
    embeddingModelId?: string;
    id: string;
    metric?: string;
    modelKey?: string;
    status?: string;
    storageType?: string;
    tableName?: string;
    updatedAt?: string;
};

export type GenerationTask = {
    createdAt?: string;
    errorCode?: string;
    errorMessage?: string;
    finishedAt?: string;
    id: string;
    mediaType?: string;
    modelId?: string;
    operation?: string;
    prompt?: string;
    resultAssetIds?: string[];
    startedAt?: string;
    status?: string;
};

type AssetFacet = {
    count?: number;
    key?: string;
};

export type AssetFacets = {
    kinds?: AssetFacet[];
    sources?: AssetFacet[];
    total?: number;
};

export type Asset = {
    contentType?: string;
    createdAt?: string;
    description?: string;
    height?: number;
    id: string;
    kind?: string;
    name?: string;
    ownerId?: string;
    size?: number;
    source?: string;
    status?: string;
    width?: number;
    workspaceId?: string;
};

export type Subject = {
    createdAt?: string;
    description?: string;
    id: string;
    kind?: string;
    name?: string;
    ownerId?: string;
    status?: string;
    visibility?: string;
    workspaceId?: string;
};

export type Work = {
    createdAt?: string;
    favoriteCount?: number;
    id: string;
    likeCount?: number;
    ownerId?: string;
    publishedAt?: string;
    status?: string;
    title?: string;
    viewCount?: number;
    visibility?: string;
};

export type Template = {
    createdAt?: string;
    description?: string;
    id: string;
    mediaType?: string;
    name?: string;
    ownerId?: string;
    publishedAt?: string;
    status?: string;
    title?: string;
    visibility?: string;
    workspaceId?: string;
};

export type Activity = {
    createdAt?: string;
    description?: string;
    endsAt?: string;
    id: string;
    ownerId?: string;
    startsAt?: string;
    status?: string;
    title?: string;
    visibility?: string;
    workspaceId?: string;
};

async function getCollection<T>(path: string, params?: Record<string, unknown>): Promise<T[]> {
    const response = await axiosInstance.get<CollectionResponse<T>>(`${API_ROOT}${path}`, { params });

    return Array.isArray(response.data) ? response.data : (response.data.items ?? []);
}

async function getPage<T>(path: string, params?: Record<string, unknown>): Promise<PageResponse<T>> {
    return (await axiosInstance.get<PageResponse<T>>(`${API_ROOT}${path}`, { params })).data;
}

export async function listAiChannels(): Promise<AiChannel[]> {
    return getCollection<AiChannel>("/aichannels/-/all");
}

export async function listAiModels(): Promise<AiModel[]> {
    return getCollection<AiModel>("/aimodels");
}

export async function listAiChannelModels(): Promise<AiChannelModel[]> {
    return getCollection<AiChannelModel>("/aichannelmodels");
}

export async function listAiDefaultModels(): Promise<AiDefaultModel[]> {
    return getCollection<AiDefaultModel>("/aidefaultmodels/-/all");
}

export async function listAiPriceRules(): Promise<AiPriceRule[]> {
    return getCollection<AiPriceRule>("/aipricerules");
}

export async function listAiQuotaPlans(): Promise<AiQuotaPlan[]> {
    return getCollection<AiQuotaPlan>("/aiquotaplans");
}

export async function listAiQuotaAssignments(): Promise<AiQuotaAssignment[]> {
    return getCollection<AiQuotaAssignment>("/aiquotaassignments");
}

export async function listCatalogEntries(kind?: string): Promise<CatalogEntry[]> {
    return getCollection<CatalogEntry>("/catalogentries", { kind });
}

export async function listAiTools(): Promise<AiToolDescriptor[]> {
    return getCollection<AiToolDescriptor>("/catalogs/-/tools");
}

export async function getAgentSessionSummary(): Promise<AgentSessionSummary> {
    return (await axiosInstance.get<AgentSessionSummary>(`${API_ROOT}/agentsessions/-/all-summary`)).data;
}

export async function listAgentSessions(limit = 100): Promise<PageResponse<AgentSession>> {
    return getPage<AgentSession>("/agentsessions/-/all", { limit });
}

export async function listSandboxes(limit = 100): Promise<PageResponse<Sandbox>> {
    return getPage<Sandbox>("/sandboxes/-/all", { limit });
}

export async function listMemoryPool(status = "published", limit = 100): Promise<MemoryPoolItem[]> {
    return (await getPage<MemoryPoolItem>("/memories/-/pool", { limit, status })).items ?? [];
}

export async function listMemoryJobs(limit = 100): Promise<MemoryJob[]> {
    return (await getPage<MemoryJob>("/memoryjobs/-/all", { limit })).items ?? [];
}

export async function listMemoryVectorSpaces(): Promise<MemoryVectorSpace[]> {
    return getCollection<MemoryVectorSpace>("/memoryvectorspaces");
}

export async function listGenerationTasks(limit = 100): Promise<PageResponse<GenerationTask>> {
    return getPage<GenerationTask>("/generationtasks/-/all", { limit });
}

export async function getAssetFacets(): Promise<AssetFacets> {
    return (await axiosInstance.get<AssetFacets>(`${API_ROOT}/assets/-/all-facets`)).data;
}

export async function listAssets(limit = 100): Promise<PageResponse<Asset>> {
    return getPage<Asset>("/assets/-/all", { limit });
}

export async function listSubjects(limit = 100): Promise<PageResponse<Subject>> {
    return getPage<Subject>("/subjects/-/all", { limit });
}

export async function listExploreWorks(limit = 100): Promise<PageResponse<Work>> {
    return getPage<Work>("/works/-/all", { limit });
}

export async function listTemplates(limit = 100): Promise<PageResponse<Template>> {
    return getPage<Template>("/templates/-/all", { limit });
}

export async function listActivities(limit = 100): Promise<PageResponse<Activity>> {
    return getPage<Activity>("/activities/-/all", { limit });
}

export type ContentKind = "activities" | "templates" | "works";

export type ContentReviewAction = "approve" | "publish" | "reject" | "unpublish";

export async function changeContentStatus(kind: ContentKind, id: string, action: ContentReviewAction): Promise<void> {
    await axiosInstance.post(`${API_ROOT}/${kind}/${id}/${action}`);
}
