import { Slider } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import type { ComposerAudioSettingsValue } from "./composer-types";

type ComposerAudioSettingsField = "speed" | "volume";

const AUDIO_SETTINGS_FIELDS = {
    speed: { label: "语速", max: 2, min: 0.5, step: 0.1 },
    volume: { label: "音量", max: 100, min: 0, step: 1 },
} as const;

export function formatComposerAudioSpeed(speed: number): string {
    return `${Number.isInteger(speed) ? speed : speed.toFixed(1)}x`;
}

type ComposerAudioSettingsProps = {
    fields: readonly ComposerAudioSettingsField[];
    onChange: (next: Partial<ComposerAudioSettingsValue>) => void;
    value: ComposerAudioSettingsValue;
};

export function ComposerAudioSettings({ fields, onChange, value }: ComposerAudioSettingsProps) {
    return (
        <div className={`${styles.theme} ${styles.audioSettingsPanel}`}>
            {fields.map((field) => {
                const meta = AUDIO_SETTINGS_FIELDS[field];
                const fieldValue = value[field];

                return (
                    <section className={styles.audioSettingsSection} key={field}>
                        <div className={styles.audioSettingsHeader}>
                            <span>{meta.label}</span>
                            <span>{field === "speed" ? formatComposerAudioSpeed(fieldValue) : fieldValue}</span>
                        </div>
                        <Slider
                            aria-label={meta.label}
                            className={styles.audioSlider}
                            max={meta.max}
                            min={meta.min}
                            onChange={(next) => onChange({ [field]: Number(next) })}
                            step={meta.step}
                            value={fieldValue}
                        />
                        <div className={styles.audioSliderLimits}>
                            <span>{meta.min}</span>
                            <span>{meta.max}</span>
                        </div>
                    </section>
                );
            })}
        </div>
    );
}
