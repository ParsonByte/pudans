"use client";

import { useMemo, useState } from "react";

import { useQuery } from "@tanstack/react-query";
import { CpuIcon, RefreshIcon } from "tdesign-icons-react";
import { Button, RadioGroup } from "tdesign-react";

import { DansPage } from "../components/page";
import {
    type AiChannelModel,
    type AiDefaultModel,
    type AiModel,
    listAiChannelModels,
    listAiDefaultModels,
    listAiModels,
} from "../composables/_dans";
import { DefaultRouting } from "./components/default-routing";
import { ModelDirectory } from "./components/model-directory";
import { RouteTable } from "./components/route-table";

const TABS = [
    { label: "逻辑模型", value: "models" },
    { label: "渠道路由", value: "routes" },
    { label: "默认模型", value: "defaults" },
] as const;

type ModelTab = (typeof TABS)[number]["value"];

export function AiModelsView() {
    const [tab, setTab] = useState<ModelTab>("models");
    const [routeKeyword, setRouteKeyword] = useState("");
    const [routeModelId, setRouteModelId] = useState("all");
    const [routeStatus, setRouteStatus] = useState("all");
    const modelsQuery = useQuery({ queryFn: listAiModels, queryKey: ["dans", "ai", "models"] });
    const routesQuery = useQuery({
        queryFn: listAiChannelModels,
        queryKey: ["dans", "ai", "channel-models"],
    });
    const defaultsQuery = useQuery({
        queryFn: listAiDefaultModels,
        queryKey: ["dans", "ai", "default-models"],
    });
    const models = useMemo<AiModel[]>(() => modelsQuery.data ?? [], [modelsQuery.data]);
    const routes = useMemo<AiChannelModel[]>(() => routesQuery.data ?? [], [routesQuery.data]);
    const defaults = useMemo<AiDefaultModel[]>(() => defaultsQuery.data ?? [], [defaultsQuery.data]);
    const routeCounts = useMemo(() => {
        const counts = new Map<string, number>();
        for (const route of routes) {
            if (!route.modelId) {
                continue;
            }
            counts.set(route.modelId, (counts.get(route.modelId) ?? 0) + 1);
        }

        return counts;
    }, [routes]);
    const defaultCounts = useMemo(() => {
        const counts = new Map<string, number>();
        for (const item of defaults) {
            if (!item.modelId) {
                continue;
            }
            counts.set(item.modelId, (counts.get(item.modelId) ?? 0) + 1);
        }

        return counts;
    }, [defaults]);
    const refreshing = modelsQuery.isFetching || routesQuery.isFetching || defaultsQuery.isFetching;
    const refresh = () => {
        void modelsQuery.refetch();
        void routesQuery.refetch();
        void defaultsQuery.refetch();
    };
    const showRoutes = (modelId: string) => {
        setRouteKeyword("");
        setRouteModelId(modelId);
        setRouteStatus("all");
        setTab("routes");
    };
    const showModels = () => setTab("models");

    return (
        <DansPage
            actions={
                <Button icon={<RefreshIcon />} loading={refreshing} onClick={refresh} variant="outline">
                    刷新
                </Button>
            }
            icon={<CpuIcon size={22} />}
            section="AI"
            showOverview={false}
            title="模型与路由"
        >
            <RadioGroup
                className="w-fit"
                onChange={(value) => setTab(String(value) as ModelTab)}
                options={TABS.map((item) => ({ label: item.label, value: item.value }))}
                theme="button"
                value={tab}
                variant="default-filled"
            />
            {tab === "models" ? (
                <ModelDirectory
                    defaultCounts={defaultCounts}
                    error={modelsQuery.error}
                    loading={modelsQuery.isPending}
                    models={models}
                    onRetry={() => void modelsQuery.refetch()}
                    onShowRoutes={showRoutes}
                    routeCounts={routeCounts}
                />
            ) : tab === "routes" ? (
                <RouteTable
                    error={routesQuery.error}
                    keyword={routeKeyword}
                    loading={routesQuery.isPending}
                    modelId={routeModelId}
                    models={models}
                    onKeywordChange={setRouteKeyword}
                    onModelChange={setRouteModelId}
                    onRetry={() => void routesQuery.refetch()}
                    onStatusChange={setRouteStatus}
                    routeCounts={routeCounts}
                    routes={routes}
                    status={routeStatus}
                />
            ) : (
                <DefaultRouting
                    defaults={defaults}
                    error={defaultsQuery.error}
                    loading={defaultsQuery.isPending}
                    models={models}
                    onRetry={() => void defaultsQuery.refetch()}
                    onShowModels={showModels}
                    onShowRoutes={showRoutes}
                    routes={routes}
                />
            )}
        </DansPage>
    );
}
