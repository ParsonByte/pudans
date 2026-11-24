import { useState } from "react";

import { ChevronDownIcon, ChevronRightIcon, SoundIcon, TimeIcon } from "tdesign-icons-react";
import { Avatar, Button, Popup } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import { ComposerAudioEmotionPanel } from "./composer-audio-emotion-panel";
import { ComposerAudioSettings, formatComposerAudioSpeed } from "./composer-audio-settings";
import type { ComposerAudioSettingsValue } from "./composer-types";

type ComposerAudioToolbarProps = {
    onChangeSettings: (next: Partial<ComposerAudioSettingsValue>) => void;
    onToggleTag: (tag: string) => void;
    selectedTags: readonly string[];
    settings: ComposerAudioSettingsValue;
};

export function ComposerAudioToolbar({
    onChangeSettings,
    onToggleTag,
    selectedTags,
    settings,
}: ComposerAudioToolbarProps) {
    const [emotionVisible, setEmotionVisible] = useState(false);
    const [speedVisible, setSpeedVisible] = useState(false);
    const [voiceVisible, setVoiceVisible] = useState(false);
    const [volumeVisible, setVolumeVisible] = useState(false);

    const closeVoiceMenu = () => setVoiceVisible(false);

    return (
        <div className={styles.audioBar}>
            <Popup
                content={<ComposerAudioEmotionPanel onToggleTag={onToggleTag} selectedTags={selectedTags} />}
                onVisibleChange={setEmotionVisible}
                overlayClassName={styles.audioPanelPopup}
                placement="top-left"
                trigger="click"
                visible={emotionVisible}
            >
                <Button className={styles.audioControl} type="button" variant="text">
                    <span aria-hidden className={styles.audioSlash}>
                        /
                    </span>
                    <span>语气标签</span>
                </Button>
            </Popup>
            <Popup
                content={
                    <div className={`${styles.theme} ${styles.audioVoiceMenu}`}>
                        <Button
                            className={styles.audioVoiceMenuItem}
                            onClick={closeVoiceMenu}
                            type="button"
                            variant="text"
                        >
                            音色管理
                        </Button>
                        <Button
                            className={styles.audioVoiceMenuItem}
                            onClick={closeVoiceMenu}
                            type="button"
                            variant="text"
                        >
                            <span>创建音色</span>
                            <ChevronRightIcon size={14} />
                        </Button>
                        <Button
                            className={styles.audioVoiceMenuItem}
                            onClick={closeVoiceMenu}
                            type="button"
                            variant="text"
                        >
                            音色库
                        </Button>
                    </div>
                }
                onVisibleChange={setVoiceVisible}
                overlayClassName={styles.audioVoicePopup}
                placement="top-left"
                trigger="click"
                visible={voiceVisible}
            >
                <Button aria-label="当前音色 Beth" className={styles.audioVoiceButton} type="button" variant="text">
                    <Avatar className={styles.audioVoiceAvatar} size="22px">
                        B
                    </Avatar>
                    <span>Beth</span>
                    <ChevronDownIcon className={styles.audioControlArrow} size={12} />
                </Button>
            </Popup>
            <Popup
                content={<ComposerAudioSettings fields={["volume"]} onChange={onChangeSettings} value={settings} />}
                onVisibleChange={setVolumeVisible}
                overlayClassName={styles.audioSettingsPopup}
                placement="top-left"
                trigger="click"
                visible={volumeVisible}
            >
                <Button
                    aria-label={`音量 ${settings.volume}`}
                    className={styles.audioControl}
                    type="button"
                    variant="text"
                >
                    <SoundIcon size={16} />
                    <span>{settings.volume}</span>
                </Button>
            </Popup>
            <span aria-hidden className={styles.audioDivider} />
            <Popup
                content={<ComposerAudioSettings fields={["speed"]} onChange={onChangeSettings} value={settings} />}
                onVisibleChange={setSpeedVisible}
                overlayClassName={styles.audioSettingsPopup}
                placement="top-left"
                trigger="click"
                visible={speedVisible}
            >
                <Button
                    aria-label={`语速 ${formatComposerAudioSpeed(settings.speed)}`}
                    className={styles.audioControl}
                    type="button"
                    variant="text"
                >
                    <TimeIcon size={16} />
                    <span>{formatComposerAudioSpeed(settings.speed)}</span>
                    <ChevronDownIcon className={styles.audioControlArrow} size={12} />
                </Button>
            </Popup>
        </div>
    );
}
