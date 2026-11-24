import type { CSSProperties } from "react";

import { Sparkles } from "lucide-react";
import { HelpCircleIcon } from "tdesign-icons-react";
import { Button, Slider, Switch, Tooltip } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import {
    COMPOSER_BACKGROUND_OPTIONS,
    COMPOSER_IMAGE_RATIO_OPTIONS,
    COMPOSER_IMAGE_RESOLUTION_OPTIONS,
    COMPOSER_MAX_DURATION,
    COMPOSER_MIN_DURATION,
    COMPOSER_RATIO_OPTIONS,
    COMPOSER_RESOLUTION_OPTIONS,
    type ComposerMediaMode,
    type ComposerSettingsValue,
    type ComposerToolMode,
} from "./composer-types";

const DURATION_MARKS = {
    1: "1s",
    5: "5s",
    10: "10s",
    15: "15s",
};

function ratioPreviewStyle(ratio: string): CSSProperties {
    const [width, height] = ratio.split(":").map(Number);
    const scale = Math.min(32 / width, 22 / height);

    return {
        height: `${height * scale}px`,
        width: `${width * scale}px`,
    };
}

type ComposerSettingsProps = {
    mediaMode: ComposerMediaMode;
    onChange: (next: Partial<ComposerSettingsValue>) => void;
    toolMode: ComposerToolMode;
    value: ComposerSettingsValue;
};

export function ComposerSettings({ mediaMode, onChange, toolMode, value }: ComposerSettingsProps) {
    const resolutionOptions = mediaMode === "image" ? COMPOSER_IMAGE_RESOLUTION_OPTIONS : COMPOSER_RESOLUTION_OPTIONS;
    const ratioOptions = mediaMode === "image" ? COMPOSER_IMAGE_RATIO_OPTIONS : COMPOSER_RATIO_OPTIONS;
    const showRatio =
        mediaMode === "image" ||
        (toolMode !== "frame" && toolMode !== "edit" && toolMode !== "motion" && toolMode !== "continue");

    return (
        <div className={`${styles.theme} ${styles.settingsPanel}`}>
            {mediaMode === "video" ? (
                <div className={styles.settingsHeader}>
                    <span className={styles.previewModeLabel}>
                        预览模式
                        <Tooltip content="预览模式">
                            <span className={styles.previewHelp}>
                                <HelpCircleIcon size={14} />
                            </span>
                        </Tooltip>
                    </span>
                    <Switch
                        className={styles.previewSwitch}
                        onChange={(previewMode) => onChange({ previewMode })}
                        size="small"
                        value={value.previewMode}
                    />
                </div>
            ) : null}
            <section className={styles.settingsCard}>
                <span className={styles.settingsLabel}>分辨率</span>
                <div
                    aria-label="分辨率"
                    className={`${styles.resolutionGrid} ${mediaMode === "image" ? styles.tripleOptionGrid : ""}`}
                    role="radiogroup"
                >
                    {resolutionOptions.map((resolution) => {
                        const active = resolution === value.resolution;

                        return (
                            <Button
                                aria-checked={active}
                                className={`${styles.optionTile} ${active ? styles.optionTileActive : ""}`}
                                key={resolution}
                                onClick={() => onChange({ resolution })}
                                role="radio"
                                type="button"
                                variant="text"
                            >
                                {resolution}
                            </Button>
                        );
                    })}
                </div>
            </section>
            {mediaMode === "image" ? (
                <section className={styles.settingsCard}>
                    <span className={styles.settingsLabel}>背景</span>
                    <div
                        aria-label="背景"
                        className={`${styles.resolutionGrid} ${styles.tripleOptionGrid}`}
                        role="radiogroup"
                    >
                        {COMPOSER_BACKGROUND_OPTIONS.map((option) => {
                            const active = option.value === value.background;

                            return (
                                <Button
                                    aria-checked={active}
                                    className={`${styles.optionTile} ${active ? styles.optionTileActive : ""}`}
                                    key={option.value}
                                    onClick={() => onChange({ background: option.value })}
                                    role="radio"
                                    type="button"
                                    variant="text"
                                >
                                    {option.label}
                                </Button>
                            );
                        })}
                    </div>
                </section>
            ) : null}
            {showRatio ? (
                <section className={styles.settingsCard}>
                    <span className={styles.settingsLabel}>宽高比</span>
                    <div
                        aria-label="宽高比"
                        className={`${styles.ratioGrid} ${mediaMode === "image" ? styles.imageRatioGrid : ""}`}
                        role="radiogroup"
                    >
                        {ratioOptions.map((ratio) => {
                            const active = ratio === value.ratio;

                            return (
                                <Button
                                    aria-checked={active}
                                    aria-label={`宽高比 ${ratio}`}
                                    className={`${styles.ratioOption} ${active ? styles.ratioOptionActive : ""}`}
                                    key={ratio}
                                    onClick={() => onChange({ ratio })}
                                    role="radio"
                                    type="button"
                                    variant="text"
                                >
                                    <span className={styles.ratioPreviewBox}>
                                        {ratio === "自动" ? (
                                            <Sparkles size={18} />
                                        ) : (
                                            <span className={styles.ratioPreview} style={ratioPreviewStyle(ratio)} />
                                        )}
                                    </span>
                                    <span>{ratio}</span>
                                </Button>
                            );
                        })}
                    </div>
                </section>
            ) : null}
            {mediaMode === "video" && toolMode !== "edit" && toolMode !== "motion" ? (
                <section className={`${styles.settingsCard} ${styles.durationCard}`}>
                    <div className={styles.settingsSectionHeader}>
                        <span className={styles.settingsLabel}>时长</span>
                        <span className={styles.durationValue}>{value.duration}s</span>
                    </div>
                    <Slider
                        aria-label="时长"
                        className={styles.durationSlider}
                        marks={DURATION_MARKS}
                        max={COMPOSER_MAX_DURATION}
                        min={COMPOSER_MIN_DURATION}
                        onChange={(duration) => onChange({ duration: Number(duration) })}
                        step={1}
                        value={value.duration}
                    />
                </section>
            ) : null}
        </div>
    );
}
