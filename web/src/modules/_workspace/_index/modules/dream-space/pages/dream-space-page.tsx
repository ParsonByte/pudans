"use client";

import { useEffect } from "react";

import dynamic from "next/dynamic";

import { DreamAudioManager } from "../components/audio/dream-audio-manager";
import { DreamSpaceBoundary } from "../components/core/dream-space-boundary";
import { DreamSpaceRoot } from "../components/core/dream-space-root";
import { WorldTransitionOverlay } from "../components/core/world-transition-overlay";
import { DreamHUD } from "../components/hud/dream-hud";
import { LoadingScreen } from "../components/hud/loading-screen";
import { MobileDrawer } from "../components/mobile/mobile-drawer";
import { MobileQualityNotice } from "../components/mobile/mobile-quality-notice";
import { TouchHUD } from "../components/mobile/touch-hud";
import { LowPowerFallback } from "../components/perf/low-power-fallback";
import { ParticleBudgetController } from "../components/perf/particle-budget-controller";
import { WebGLFallbackView } from "../components/perf/webgl-fallback-view";
import { WorldGeneratorBridge } from "../components/world/world-generator-bridge";
import { useWebGLCapability } from "../composables/use-webgl-capability";
import { DREAM_PAGE_SUBTITLE, DREAM_PAGE_TITLE } from "../constants";
import { setDreamPerformanceMode } from "../stores/dream-store";
import { useDreamStore } from "../stores/use-dream-store";
import styles from "../styles/dream-space.module.css";

const DreamCanvas = dynamic(() => import("../components/core/dream-canvas").then((module) => module.DreamCanvas), {
    loading: () => <LoadingScreen />,
    ssr: false,
});

function DreamSpaceContent() {
    const capability = useWebGLCapability();
    const world = useDreamStore((state) => state.world);
    const phase = useDreamStore((state) => state.phase);
    const highContrast = useDreamStore((state) => state.highContrast);
    const activeRegionId = useDreamStore((state) => state.activeRegionId);

    useEffect(() => {
        if (capability.lowPower) {
            setDreamPerformanceMode(true);
        }
    }, [capability.lowPower]);

    if (!capability.checked) {
        return <LoadingScreen />;
    }

    if (!capability.supported) {
        return <WebGLFallbackView />;
    }

    if (!world) {
        return <LoadingScreen />;
    }

    return (
        <section
            aria-label={DREAM_PAGE_TITLE}
            className={`${styles.page} ${highContrast ? styles.highContrast : ""}`}
            data-dream-phase={phase}
            data-dream-subtitle={DREAM_PAGE_SUBTITLE}
            data-region={activeRegionId}
        >
            <LowPowerFallback lowPower={capability.lowPower} webgl={capability.supported} />
            <ParticleBudgetController />
            <DreamSpaceBoundary />
            <DreamAudioManager />
            <WorldGeneratorBridge>
                <div className={styles.sceneStage}>
                    <DreamCanvas />
                    <div aria-hidden className={styles.sceneVignette} />
                </div>
            </WorldGeneratorBridge>
            {phase === "transition" ? <WorldTransitionOverlay /> : null}
            <DreamHUD />
            <TouchHUD />
            <MobileDrawer />
            <MobileQualityNotice lowPower={capability.lowPower} />
        </section>
    );
}

export function DreamSpacePage() {
    return (
        <DreamSpaceRoot>
            <DreamSpaceContent />
        </DreamSpaceRoot>
    );
}
