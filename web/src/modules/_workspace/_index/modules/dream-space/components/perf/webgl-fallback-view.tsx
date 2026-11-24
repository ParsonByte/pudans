"use client";

import { useMemo } from "react";

import { Compass, Sparkles } from "lucide-react";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { filterMemories } from "../../world/memory-queries";
import { regionById } from "../../world/world-generator";
import { SeedBadge } from "../hud/seed-badge";
import { SessionBadge } from "../hud/session-badge";
import { MemoryListFallback } from "../panels/memory-list-fallback";
import { PortalRegistryView } from "../portals/portal-registry-view";

export function WebGLFallbackView() {
    const world = useDreamStore((state) => state.world);
    const filters = useDreamStore((state) => state.filters);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);
    const memories = useMemo(() => (world ? filterMemories(world.memories, filters) : []), [filters, world]);

    if (!world) {
        return null;
    }

    const region = regionById(world, activeRegionId);

    return (
        <main className={styles.fallbackPage}>
            <header className={styles.fallbackHeader}>
                <div>
                    <span className={styles.externalBadge}>
                        <Compass aria-hidden size={11} />
                        2D DREAM MAP
                    </span>
                    <h1>{region.name}</h1>
                    <p>
                        当前设备未启用
                        WebGL，幻境已降级为记忆星图与门户列表。筛选、详情、灵感生成和外部独立页面仍然可用。
                    </p>
                </div>
                <div className="flex flex-wrap gap-2">
                    <SeedBadge />
                    <SessionBadge />
                </div>
            </header>
            <div className={styles.fallbackGrid}>
                <section className={styles.fallbackPanel}>
                    <h2>记忆星辰 · {memories.length}</h2>
                    <MemoryListFallback memories={memories} />
                </section>
                <section className={styles.fallbackPanel}>
                    <h2>梦境门户</h2>
                    <PortalRegistryView />
                    <p className={styles.portalHint}>
                        <Sparkles aria-hidden size={11} />
                        点击记忆卡片仍会打开详情；外部页面在独立路由和独立浏览器标签中打开。
                    </p>
                </section>
            </div>
        </main>
    );
}
