"use client";

import { Accessibility, Gauge, Headphones, ScanLine, Sparkles } from "lucide-react";

import {
    setDreamAudioEnabled,
    setDreamCameraMode,
    setDreamDebugVisible,
    setDreamHighContrast,
    setDreamPerformanceMode,
    setDreamReducedMotion,
    toggleDreamPanel,
} from "../../stores/dream-store";
import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";
import { HudPanel } from "./hud-panel";

function SettingToggle({
    checked,
    description,
    icon,
    label,
    onChange,
}: {
    checked: boolean;
    description: string;
    icon: React.ReactNode;
    label: string;
    onChange: (checked: boolean) => void;
}) {
    return (
        <label className={styles.subPanel}>
            <span className="flex items-start gap-2">
                <span className="mt-0.5 text-white/45">{icon}</span>
                <span className="min-w-0 flex-1">
                    <strong className="block text-[10px] font-medium text-white/76">{label}</strong>
                    <span className="mt-1 block text-[8px] leading-relaxed text-white/34">{description}</span>
                </span>
                <input
                    checked={checked}
                    className="mt-0.5"
                    onChange={(event) => onChange(event.target.checked)}
                    type="checkbox"
                />
            </span>
        </label>
    );
}

export function SettingsPanel() {
    const cameraMode = useDreamStore((state) => state.cameraMode);
    const reducedMotion = useDreamStore((state) => state.reducedMotion);
    const performanceMode = useDreamStore((state) => state.performanceMode);
    const audioEnabled = useDreamStore((state) => state.audioEnabled);
    const highContrast = useDreamStore((state) => state.highContrast);
    const debugVisible = useDreamStore((state) => state.debugVisible);

    return (
        <HudPanel
            className={styles.widePanel}
            kicker="WORLD SETTINGS"
            onClose={() => toggleDreamPanel("settings")}
            title="梦境设置"
        >
            <div className={styles.filterGroup}>
                <span className={styles.filterLabel}>视角</span>
                <div className={styles.filterOptions}>
                    <button
                        className={`${styles.pill} ${cameraMode === "third" ? styles.pillActive : ""}`}
                        onClick={() => setDreamCameraMode("third")}
                        type="button"
                    >
                        第三人称
                    </button>
                    <button
                        className={`${styles.pill} ${cameraMode === "first" ? styles.pillActive : ""}`}
                        onClick={() => setDreamCameraMode("first")}
                        type="button"
                    >
                        第一人称
                    </button>
                </div>
            </div>
            <div className="mt-3 flex flex-col gap-2">
                <SettingToggle
                    checked={reducedMotion}
                    description="关闭自动漂浮、粒子流动与强闪烁。"
                    icon={<Accessibility aria-hidden size={13} />}
                    label="减少动态效果"
                    onChange={setDreamReducedMotion}
                />
                <SettingToggle
                    checked={performanceMode}
                    description="降低粒子数量、阴影和后处理，优先保证帧率。"
                    icon={<Gauge aria-hidden size={13} />}
                    label="性能优先"
                    onChange={setDreamPerformanceMode}
                />
                <SettingToggle
                    checked={highContrast}
                    description="提高 HUD 边界与文字对比度。"
                    icon={<Sparkles aria-hidden size={13} />}
                    label="高对比界面"
                    onChange={setDreamHighContrast}
                />
                <SettingToggle
                    checked={audioEnabled}
                    description="启用合成环境音与空间提示音。"
                    icon={<Headphones aria-hidden size={13} />}
                    label="梦境声音"
                    onChange={setDreamAudioEnabled}
                />
                <SettingToggle
                    checked={debugVisible}
                    description="显示世界种子、区域和碰撞体调试信息。"
                    icon={<ScanLine aria-hidden size={13} />}
                    label="调试视图"
                    onChange={setDreamDebugVisible}
                />
            </div>
            <p className={styles.portalHint}>
                <ScanLine aria-hidden size={11} />
                调试视图只在开发环境建议开启。外部页面始终在独立路由中加载。
            </p>
        </HudPanel>
    );
}
