"use client";

import { Canvas } from "@react-three/fiber";
import { Bloom, EffectComposer, Vignette } from "@react-three/postprocessing";
import { Physics } from "@react-three/rapier";
import * as THREE from "three";

import { WORLD_GRAVITY } from "../../constants";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { ColliderDebugView } from "../debug/collider-debug-view";
import { PerformanceMonitor } from "../perf/performance-monitor";
import { QualityAutoScaler } from "../perf/quality-auto-scaler";
import { RenderPauseOnHidden } from "../perf/render-pause-on-hidden";
import { SceneRouter } from "./scene-router";

export function DreamCanvas() {
    const paused = useDreamStore((state) => state.paused);
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);
    const debugVisible = useDreamStore((state) => state.debugVisible);

    return (
        <div className={styles.canvasStage} data-dream-canvas>
            <Canvas
                camera={{ far: 280, fov: 58, near: 0.08, position: [0, 4, 9] }}
                dpr={performanceMode ? 0.85 : [1, 1.5]}
                gl={{
                    alpha: false,
                    antialias: !performanceMode,
                    powerPreference: "high-performance",
                    toneMapping: THREE.ACESFilmicToneMapping,
                }}
                onCreated={({ gl, scene }) => {
                    gl.setClearColor("#040612");
                    gl.toneMappingExposure = 1.06;
                    scene.background = new THREE.Color("#040612");
                }}
                shadows={!performanceMode}
            >
                <QualityAutoScaler />
                <PerformanceMonitor />
                <RenderPauseOnHidden />
                <ColliderDebugView />
                <Physics debug={debugVisible} gravity={[0, -WORLD_GRAVITY, 0]} paused={paused} timeStep="vary">
                    <SceneRouter />
                </Physics>
                {!performanceMode ? (
                    <EffectComposer multisampling={0}>
                        <Bloom intensity={reducedMotion ? 0.55 : 0.9} luminanceThreshold={0.62} mipmapBlur />
                        <Vignette darkness={0.42} offset={0.18} />
                    </EffectComposer>
                ) : null}
            </Canvas>
        </div>
    );
}
