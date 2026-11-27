import { useState } from "react";

import {
    AiImageIcon,
    AiVideoIcon,
    AudioIcon,
    ControlPlatformIcon,
    HelpCircleIcon,
    ThunderIcon,
} from "tdesign-icons-react";
import { Button, InputNumber, Popup, Switch, Tooltip } from "tdesign-react";

import styles from "../../styles/floating-chat-composer.module.css";
import { ComposerAdvancedSettings } from "./composer-advanced-settings";
import { ComposerAudioToolbar } from "./composer-audio-toolbar";
import { ComposerModelPanel } from "./composer-model-panel";
import { ComposerSettings } from "./composer-settings";
import {
    type ComposerAdvancedSettingsValue,
    type ComposerAudioSettingsValue,
    type ComposerMediaMode,
    type ComposerModelOption,
    type ComposerModelValue,
    type ComposerSettingsValue,
    type ComposerToolMode,
    getComposerBackgroundLabel,
} from "./composer-types";

type ComposerToolbarProps = {
    advancedSettings: ComposerAdvancedSettingsValue;
    audioEnabled: boolean;
    audioSettings: ComposerAudioSettingsValue;
    canSubmit: boolean;
    characterCount: number;
    maxLength: number;
    mediaMode: ComposerMediaMode;
    model: ComposerModelValue;
    models: readonly ComposerModelOption[];
    modelsLoading: boolean;
    multiCamera: boolean;
    onChangeAdvancedSettings: (next: Partial<ComposerAdvancedSettingsValue>) => void;
    onChangeAudioEnabled: (audioEnabled: boolean) => void;
    onChangeAudioSettings: (next: Partial<ComposerAudioSettingsValue>) => void;
    onChangeModel: (model: ComposerModelValue) => void;
    onChangeMultiCamera: (multiCamera: boolean) => void;
    onChangeQuantity: (quantity: number) => void;
    onChangeSettings: (next: Partial<ComposerSettingsValue>) => void;
    onSubmit: () => void;
    quantity: number;
    selectedAudioTags: readonly string[];
    settings: ComposerSettingsValue;
    submitting: boolean;
    toolMode: ComposerToolMode;
    onToggleAudioTag: (tag: string) => void;
};

export function ComposerToolbar({
    advancedSettings,
    audioEnabled,
    audioSettings,
    canSubmit,
    characterCount,
    maxLength,
    mediaMode,
    model,
    models,
    modelsLoading,
    multiCamera,
    onChangeAdvancedSettings,
    onChangeAudioEnabled,
    onChangeAudioSettings,
    onChangeModel,
    onChangeMultiCamera,
    onChangeQuantity,
    onChangeSettings,
    onSubmit,
    quantity,
    selectedAudioTags,
    settings,
    submitting,
    toolMode,
    onToggleAudioTag,
}: ComposerToolbarProps) {
    const [advancedVisible, setAdvancedVisible] = useState(false);
    const [modelVisible, setModelVisible] = useState(false);
    const [settingsVisible, setSettingsVisible] = useState(false);
    const isAudioMode = mediaMode === "audio";
    const currentModel = models.find((option) => option.id === model);
    const currentModelLabel = currentModel?.label ?? (modelsLoading ? "加载模型" : "暂无模型");
    const backgroundLabel = getComposerBackgroundLabel(settings.background);
    const settingsSummary =
        toolMode === "frame" || toolMode === "continue"
            ? `${settings.resolution}，${settings.duration} 秒`
            : toolMode === "edit" || toolMode === "motion"
              ? settings.resolution
              : mediaMode === "video"
                ? `${settings.resolution}，${settings.ratio}，${settings.duration} 秒`
                : `${settings.resolution}，${backgroundLabel}，${settings.ratio}`;
    const showAudio =
        mediaMode === "video" &&
        (toolMode === "text" || toolMode === "reference" || toolMode === "frame" || toolMode === "continue");
    const showConfig =
        mediaMode === "video" &&
        (toolMode === "text" ||
            toolMode === "reference" ||
            toolMode === "frame" ||
            toolMode === "edit" ||
            toolMode === "motion" ||
            toolMode === "continue");
    const showMultiCamera = toolMode === "text";

    return (
        <div className={`${styles.toolbar} ${isAudioMode ? styles.audioToolbar : ""}`}>
            <span className={styles.counter}>
                {characterCount}/{maxLength}
            </span>
            <div className={styles.toolbarMain}>
                <div className={styles.toolbarLeading}>
                    {isAudioMode ? (
                        <ComposerAudioToolbar
                            onChangeSettings={onChangeAudioSettings}
                            onToggleTag={onToggleAudioTag}
                            selectedTags={selectedAudioTags}
                            settings={audioSettings}
                        />
                    ) : (
                        <Popup
                            content={
                                <ComposerSettings
                                    mediaMode={mediaMode}
                                    onChange={onChangeSettings}
                                    toolMode={toolMode}
                                    value={settings}
                                />
                            }
                            onVisibleChange={setSettingsVisible}
                            overlayClassName={styles.settingsPopup}
                            placement="top-left"
                            trigger="click"
                            visible={settingsVisible}
                        >
                            <Button
                                aria-label={`生成配置：${settingsSummary}`}
                                className={styles.settingsCapsule}
                                type="button"
                                variant="text"
                            >
                                <span>{settings.resolution}</span>
                                {mediaMode === "image" ? (
                                    <>
                                        <span className={styles.capsuleDivider} />
                                        <span>{backgroundLabel}</span>
                                        <span className={styles.capsuleDivider} />
                                        <span>{settings.ratio}</span>
                                    </>
                                ) : (
                                    <>
                                        {toolMode !== "frame" &&
                                        toolMode !== "edit" &&
                                        toolMode !== "motion" &&
                                        toolMode !== "continue" ? (
                                            <>
                                                <span className={styles.capsuleDivider} />
                                                <span>{settings.ratio}</span>
                                            </>
                                        ) : null}
                                        {toolMode !== "edit" && toolMode !== "motion" ? (
                                            <>
                                                <span className={styles.capsuleDivider} />
                                                <span>{settings.duration}s</span>
                                            </>
                                        ) : null}
                                    </>
                                )}
                            </Button>
                        </Popup>
                    )}
                    {!isAudioMode && (showAudio || showConfig) ? (
                        <div className={styles.textTools}>
                            {showAudio ? (
                                <span className={styles.toggleOption}>
                                    <span className={styles.toggleLabel}>
                                        音频
                                        <Tooltip content="音频">
                                            <span className={styles.previewHelp}>
                                                <HelpCircleIcon size={14} />
                                            </span>
                                        </Tooltip>
                                    </span>
                                    <Switch
                                        aria-label="音频"
                                        className={styles.gradientSwitch}
                                        onChange={onChangeAudioEnabled}
                                        size="small"
                                        value={audioEnabled}
                                    />
                                </span>
                            ) : null}
                            {showMultiCamera ? (
                                <span className={styles.toggleOption}>
                                    <span className={styles.toggleLabel}>
                                        多镜头
                                        <Tooltip content="多镜头">
                                            <span className={styles.previewHelp}>
                                                <HelpCircleIcon size={14} />
                                            </span>
                                        </Tooltip>
                                    </span>
                                    <Switch
                                        aria-label="多镜头"
                                        className={styles.gradientSwitch}
                                        onChange={onChangeMultiCamera}
                                        size="small"
                                        value={multiCamera}
                                    />
                                </span>
                            ) : null}
                            {showConfig ? (
                                <Popup
                                    content={
                                        <ComposerAdvancedSettings
                                            onChange={onChangeAdvancedSettings}
                                            value={advancedSettings}
                                        />
                                    }
                                    onVisibleChange={setAdvancedVisible}
                                    overlayClassName={styles.advancedPopup}
                                    placement="top-left"
                                    trigger="click"
                                    visible={advancedVisible}
                                >
                                    <Button
                                        aria-label="配置面板"
                                        className={styles.advancedButton}
                                        icon={<ControlPlatformIcon size={16} />}
                                        type="button"
                                        variant="text"
                                    >
                                        配置面板
                                        <span aria-hidden className={styles.advancedBadge} />
                                    </Button>
                                </Popup>
                            ) : null}
                        </div>
                    ) : null}
                </div>
                <div className={styles.toolbarActions}>
                    <Popup
                        content={
                            <ComposerModelPanel
                                loading={modelsLoading}
                                mediaMode={mediaMode}
                                models={models}
                                onChange={(nextModel) => {
                                    onChangeModel(nextModel);
                                    setModelVisible(false);
                                }}
                                value={model}
                            />
                        }
                        onVisibleChange={setModelVisible}
                        overlayClassName={styles.modelPopup}
                        placement="top-right"
                        trigger="click"
                        visible={modelVisible}
                    >
                        <Button
                            aria-label={`模型：${currentModelLabel}`}
                            className={styles.modelButton}
                            icon={
                                mediaMode === "video" ? (
                                    <AiVideoIcon />
                                ) : mediaMode === "audio" ? (
                                    <AudioIcon />
                                ) : (
                                    <AiImageIcon />
                                )
                            }
                            type="button"
                            variant="text"
                        >
                            {currentModelLabel}
                        </Button>
                    </Popup>
                    {isAudioMode ? null : (
                        <InputNumber
                            className={styles.quantity}
                            max={9}
                            min={1}
                            onChange={(value) => onChangeQuantity(Number(value) || 1)}
                            size="small"
                            theme="row"
                            value={quantity}
                        />
                    )}
                    <Button
                        className={styles.sendButton}
                        disabled={!canSubmit || submitting}
                        loading={submitting}
                        onClick={onSubmit}
                        type="button"
                    >
                        生成
                        {submitting ? null : <ThunderIcon />}
                    </Button>
                </div>
            </div>
        </div>
    );
}
