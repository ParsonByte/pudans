import { Dices } from "lucide-react";
import { HelpCircleIcon } from "tdesign-icons-react";
import { Button, Input, Slider, Switch, Tooltip } from "tdesign-react";

import { COMPOSER_MAX_SEED, type ComposerAdvancedSettingsValue } from "./composer-types";
import styles from "./floating-chat-composer.module.css";

const SEED_SLIDER_MAX = 1000;

type ComposerAdvancedSettingsProps = {
    onChange: (next: Partial<ComposerAdvancedSettingsValue>) => void;
    value: ComposerAdvancedSettingsValue;
};

function normalizeSeed(value: string): number {
    const digits = value.replace(/\D/g, "").slice(0, 9);

    return digits ? Math.min(Number(digits), COMPOSER_MAX_SEED) : 0;
}

function createRandomSeed(): number {
    return Math.floor(Math.random() * (COMPOSER_MAX_SEED + 1));
}

export function ComposerAdvancedSettings({ onChange, value }: ComposerAdvancedSettingsProps) {
    return (
        <div className={`${styles.theme} ${styles.advancedPanel}`}>
            <div className={styles.advancedHeader}>
                <span className={styles.previewModeLabel}>
                    错峰模式
                    <Tooltip content="错峰模式">
                        <span className={styles.previewHelp}>
                            <HelpCircleIcon size={14} />
                        </span>
                    </Tooltip>
                </span>
                <Switch
                    className={styles.previewSwitch}
                    onChange={(stagger) => onChange({ stagger })}
                    size="small"
                    value={value.stagger}
                />
            </div>
            <section className={styles.settingsCard}>
                <span className={styles.settingsLabel}>种子值</span>
                <div className={styles.seedRow}>
                    <Input
                        className={styles.seedInput}
                        maxlength={9}
                        onChange={(seed) => onChange({ seed: normalizeSeed(seed) })}
                        value={String(value.seed)}
                    />
                    <Button
                        aria-label="随机种子"
                        className={styles.diceButton}
                        icon={<Dices size={18} />}
                        onClick={() => onChange({ seed: createRandomSeed() })}
                        shape="square"
                        type="button"
                        variant="text"
                    />
                </div>
                <Slider
                    aria-label="种子值"
                    className={styles.seedSlider}
                    max={SEED_SLIDER_MAX}
                    min={0}
                    onChange={(seed) =>
                        onChange({
                            seed: Math.round((Number(seed) / SEED_SLIDER_MAX) * COMPOSER_MAX_SEED),
                        })
                    }
                    step={1}
                    value={Math.round((value.seed / COMPOSER_MAX_SEED) * SEED_SLIDER_MAX)}
                />
            </section>
        </div>
    );
}
