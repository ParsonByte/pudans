import {
    type AiChannelCreateRequest,
    type AiChannelModelCreateRequest,
    type AiChannelModelUpdateRequest,
    type AiChannelUpdateRequest,
    type AiModelCreateRequest,
    type AiModelUpdateRequest,
    type AiChannel as ApiAiChannel,
    type AiChannelModel as ApiAiChannelModel,
    type AiChannelTestResult as ApiAiChannelTestResult,
    type AiDefaultModel as ApiAiDefaultModel,
    type AiModel as ApiAiModel,
    type AiModelImportResult as ApiAiModelImportResult,
    type AiPriceRule as ApiAiPriceRule,
    type AiProviderDescriptor as ApiAiProviderDescriptor,
    type AiQuotaAssignment as ApiAiQuotaAssignment,
    type AiQuotaPlan as ApiAiQuotaPlan,
    type AiToolDescriptor as ApiAiToolDescriptor,
    type CatalogEntry as ApiCatalogEntry,
    apiClient,
    axiosInstance,
} from "@pudans-dev/api-client";

const API_ROOT = "/apis/api.pudans.tolim.cn/v1alpha1";

type JsonObject = Record<string, unknown>;
type AiChannelInput = Omit<AiChannelCreateRequest, "config"> & { config?: JsonObject };
type AiChannelUpdateInput = Omit<AiChannelUpdateRequest, "config"> & { config?: JsonObject };
type AiModelCreateInput = Omit<AiModelCreateRequest, "capabilities" | "parameterSchema"> & {
    capabilities?: JsonObject;
    parameterSchema?: JsonObject;
};
type AiModelUpdateInput = Omit<AiModelUpdateRequest, "capabilities" | "parameterSchema"> & {
    capabilities?: JsonObject;
    parameterSchema?: JsonObject;
};
type AiChannelModelInput = Omit<AiChannelModelCreateRequest, "parameterOverrides" | "providerMetadata"> & {
    parameterOverrides?: JsonObject;
    providerMetadata?: JsonObject;
};
type AiChannelModelUpdateInput = Omit<AiChannelModelUpdateRequest, "parameterOverrides" | "providerMetadata"> & {
    parameterOverrides?: JsonObject;
    providerMetadata?: JsonObject;
};

type CollectionResponse<T> = T[] | { items?: T[] };

type PageResponse<T> = {
    items?: T[];
    nextCursor?: string;
};

export type AiChannel = ApiAiChannel & { id: string };
export type AiModel = ApiAiModel & { id: string };
export type AiChannelModel = ApiAiChannelModel & { id: string };
export type AiProviderDescriptor = ApiAiProviderDescriptor & {
    displayName: string;
    logoUrl?: string;
    provider: string;
};
export type AiChannelTestResult = ApiAiChannelTestResult & { success: boolean };
export type AiModelImportResult = ApiAiModelImportResult & { channelId: string };
export type AiDefaultModel = ApiAiDefaultModel & { id: string };
export type AiToolDescriptor = ApiAiToolDescriptor & { key: string };
export type AiPriceRule = ApiAiPriceRule & { id: string };
export type AiQuotaPlan = ApiAiQuotaPlan & { id: string };
export type AiQuotaAssignment = ApiAiQuotaAssignment & { id: string };
export type CatalogEntry = ApiCatalogEntry & { id: string };

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
    return (await apiClient.aiChannel.listAllAiChannels()).data as AiChannel[];
}

export async function listWorkspaceAiChannels(): Promise<AiChannel[]> {
    return (await apiClient.aiChannel.listAiChannels()).data as AiChannel[];
}

export async function createAiChannel(input: AiChannelInput): Promise<AiChannel> {
    return (
        await apiClient.aiChannel.createAiChannel({
            aiChannelCreateRequest: input as unknown as AiChannelCreateRequest,
        })
    ).data as AiChannel;
}

export async function updateAiChannel(name: string, input: AiChannelUpdateInput): Promise<AiChannel> {
    return (
        await apiClient.aiChannel.updateAiChannel({
            aiChannelUpdateRequest: input as unknown as AiChannelUpdateRequest,
            name,
        })
    ).data as AiChannel;
}

export async function testAiChannel(name: string, model?: string): Promise<AiChannelTestResult> {
    return (
        await apiClient.aiChannel.testAiChannel({
            aiChannelTestRequest: { model },
            name,
        })
    ).data as AiChannelTestResult;
}

export async function importAiChannelModels(name: string, modelKeys?: string[]): Promise<AiModelImportResult> {
    return (
        await apiClient.aiChannel.importAiChannelModels({
            aiModelImportRequest: { modelKeys: modelKeys ?? [] },
            name,
        })
    ).data as AiModelImportResult;
}

export async function listAiProviders(): Promise<AiProviderDescriptor[]> {
    return (await apiClient.aiProvider.listAiProviders()).data as AiProviderDescriptor[];
}

export async function listAiModels(): Promise<AiModel[]> {
    return (await apiClient.aiModel.listAiModels()).data as AiModel[];
}

export async function createAiModel(input: AiModelCreateInput): Promise<AiModel> {
    return (
        await apiClient.aiModel.createAiModel({
            aiModelCreateRequest: input as unknown as AiModelCreateRequest,
        })
    ).data as AiModel;
}

export async function updateAiModel(name: string, input: AiModelUpdateInput): Promise<AiModel> {
    return (
        await apiClient.aiModel.updateAiModel({
            aiModelUpdateRequest: input as unknown as AiModelUpdateRequest,
            name,
        })
    ).data as AiModel;
}

export async function deleteAiModel(name: string): Promise<void> {
    await apiClient.aiModel.deleteAiModel({ name });
}

export async function listAiChannelModels(): Promise<AiChannelModel[]> {
    return (await apiClient.aiChannelModel.listAiChannelModels()).data as AiChannelModel[];
}

export async function createAiChannelModel(input: AiChannelModelInput): Promise<AiChannelModel> {
    return (
        await apiClient.aiChannelModel.createAiChannelModel({
            aiChannelModelCreateRequest: input as unknown as AiChannelModelCreateRequest,
        })
    ).data as AiChannelModel;
}

export async function updateAiChannelModel(name: string, input: AiChannelModelUpdateInput): Promise<AiChannelModel> {
    return (
        await apiClient.aiChannelModel.updateAiChannelModel({
            aiChannelModelUpdateRequest: input as unknown as AiChannelModelUpdateRequest,
            name,
        })
    ).data as AiChannelModel;
}

export async function listAiDefaultModels(): Promise<AiDefaultModel[]> {
    return (await apiClient.aiDefaultModel.listAllAiDefaultModels()).data as AiDefaultModel[];
}

export async function listAiPriceRules(): Promise<AiPriceRule[]> {
    return (await apiClient.aiPriceRule.listAiPriceRules()).data as AiPriceRule[];
}

export async function listAiQuotaPlans(): Promise<AiQuotaPlan[]> {
    return (await apiClient.aiQuotaPlan.listAiQuotaPlans()).data as AiQuotaPlan[];
}

export async function listAiQuotaAssignments(): Promise<AiQuotaAssignment[]> {
    return (await apiClient.aiQuotaAssignment.listAiQuotaAssignments()).data as AiQuotaAssignment[];
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
