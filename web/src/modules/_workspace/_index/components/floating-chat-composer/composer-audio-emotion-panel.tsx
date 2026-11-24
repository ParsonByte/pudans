import { useState } from "react";

import { CheckIcon, ChevronRightIcon } from "tdesign-icons-react";
import { Button } from "tdesign-react";

import { COMPOSER_AUDIO_ACTION_TAGS, COMPOSER_AUDIO_EMOTION_TAGS } from "./composer-types";
import styles from "./floating-chat-composer.module.css";

const AUDIO_TAG_CATEGORIES = [
    { key: "emotion", label: "情绪语气", tags: COMPOSER_AUDIO_EMOTION_TAGS },
    { key: "action", label: "声音动作", tags: COMPOSER_AUDIO_ACTION_TAGS },
] as const;

type AudioTagCategory = (typeof AUDIO_TAG_CATEGORIES)[number]["key"];

type ComposerAudioEmotionPanelProps = {
    onToggleTag: (tag: string) => void;
    selectedTags: readonly string[];
};

export function ComposerAudioEmotionPanel({ onToggleTag, selectedTags }: ComposerAudioEmotionPanelProps) {
    const [category, setCategory] = useState<AudioTagCategory>("emotion");
    const activeCategory = AUDIO_TAG_CATEGORIES.find((item) => item.key === category) ?? AUDIO_TAG_CATEGORIES[0];

    return (
        <div className={`${styles.theme} ${styles.audioTagPanel}`}>
            <div aria-label="语气标签分类" className={styles.audioTagCategories} role="tablist">
                {AUDIO_TAG_CATEGORIES.map((item) => {
                    const active = item.key === category;

                    return (
                        <Button
                            aria-selected={active}
                            className={`${styles.audioTagCategory} ${active ? styles.audioTagCategoryActive : ""}`}
                            key={item.key}
                            onClick={() => setCategory(item.key)}
                            role="tab"
                            type="button"
                            variant="text"
                        >
                            <span>{item.label}</span>
                            <ChevronRightIcon size={14} />
                        </Button>
                    );
                })}
            </div>
            <div
                aria-label={activeCategory.label}
                aria-multiselectable
                className={styles.audioTagOptions}
                role="listbox"
            >
                {activeCategory.tags.map((tag) => {
                    const selected = selectedTags.includes(tag);

                    return (
                        <Button
                            aria-selected={selected}
                            className={`${styles.audioTagOption} ${selected ? styles.audioTagOptionActive : ""}`}
                            key={tag}
                            onClick={() => onToggleTag(tag)}
                            role="option"
                            type="button"
                            variant="text"
                        >
                            <span>{tag}</span>
                            {selected ? <CheckIcon size={15} /> : null}
                        </Button>
                    );
                })}
            </div>
        </div>
    );
}
