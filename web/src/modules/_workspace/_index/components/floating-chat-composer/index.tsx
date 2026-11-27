"use client";

import { type ChangeEvent, useEffect, useRef, useState } from "react";

import { type TdChatSenderParams } from "@tdesign-react/chat";
import { type Transition, motion } from "motion/react";

import styles from "../../styles/floating-chat-composer.module.css";
import { ChatSenderWithSlots } from "../chat-sender-with-slots";
import { ComposerCollapsedBar } from "./composer-collapsed-bar";
import { ComposerFrameUpload } from "./composer-frame-upload";
import { ComposerModeBar } from "./composer-mode-bar";
import { ComposerMotionUpload } from "./composer-motion-upload";
import { ComposerSingleVideoUpload } from "./composer-single-video-upload";
import { ComposerToolbar } from "./composer-toolbar";
import {
    COMPOSER_AUDIO_TAGS,
    type ComposerAudioSettingsValue,
    type ComposerMediaMode,
    type ComposerModelOption,
    type ComposerModelValue,
    type ComposerSettingsValue,
    type ComposerSubmission,
    type ComposerToolMode,
    DEFAULT_COMPOSER_ADVANCED_SETTINGS,
    DEFAULT_COMPOSER_AUDIO_SETTINGS,
    DEFAULT_COMPOSER_IMAGE_RESOLUTION,
    DEFAULT_COMPOSER_MEDIA_MODE,
    DEFAULT_COMPOSER_SETTINGS,
    DEFAULT_COMPOSER_VIDEO_RESOLUTION,
    getComposerToolModes,
    getDefaultComposerToolMode,
} from "./composer-types";
import { ComposerUpload } from "./composer-upload";

const IMAGE_MAX_PROMPT_LENGTH = 20000;
const VIDEO_MAX_PROMPT_LENGTH = 5000;
const AUDIO_MAX_PROMPT_LENGTH = 5000;
const VIDEO_TEXT_PLACEHOLDER = "上传参考图片并输入文字，让 DANS 生成视频";
const IMAGE_DEFAULT_PLACEHOLDER = "描述您想让 DANS 创作的图片，例如：一张具有高级感的香水产品海报";
const REFERENCE_PLACEHOLDER = "上传参考图或视频，使用 DANS 创作或编辑视频，例如：将 @video1 中的人物替换为 @image1";
const FRAME_PLACEHOLDER = "描述您想让 DANS 在首帧和尾帧之间创作的内容";
const EDIT_VIDEO_PLACEHOLDER = "描述您想让 DANS 如何编辑这段视频。输入 @ 可选择元素、图片或模式。";
const CONTINUE_PLACEHOLDER = "描述您想让 DANS 如何续写这段视频";
const MOTION_PLACEHOLDER = "上传角色图片和动作视频，让 DANS 为角色制作动画";
const AUDIO_PLACEHOLDER = "输入要转换为语音的文字，例如：欢迎使用 DANS 创作";
const AUDIO_TAG_PATTERN = /^【([^】]+)】/;
const COMPOSER_MODE_BAR_HEIGHT = 48;
const COMPOSER_HEIGHT_TRANSITION: Transition = { type: "spring", stiffness: 440, damping: 38, mass: 0.85 };
const DEFAULT_AVAILABLE_MEDIA_MODES: readonly ComposerMediaMode[] = ["video", "image"];
const EMPTY_MODEL_OPTIONS: readonly ComposerModelOption[] = [];
const EMPTY_MODELS_BY_MEDIA_MODE: Partial<Record<ComposerMediaMode, readonly ComposerModelOption[]>> = {};

type ChatAttachment = NonNullable<TdChatSenderParams["attachments"]>[number];

type FloatingChatComposerProps = {
    availableMediaModes?: readonly ComposerMediaMode[];
    defaultMediaMode?: ComposerMediaMode;
    modelsByMediaMode?: Partial<Record<ComposerMediaMode, readonly ComposerModelOption[]>>;
    modelsLoading?: boolean;
    onSend?: (submission: ComposerSubmission) => Promise<void>;
    placeholder?: string;
};

function toAttachment(file: File): ChatAttachment {
    return {
        key: crypto.randomUUID(),
        name: file.name,
        raw: file,
        size: file.size,
        status: "success",
        type: file.type,
    };
}

function splitLeadingAudioTags(value: string): { content: string; tags: string[] } {
    const tags: string[] = [];
    let content = value;

    while (true) {
        const match = AUDIO_TAG_PATTERN.exec(content);
        const tag = match?.[1];

        if (!tag || !(COMPOSER_AUDIO_TAGS as readonly string[]).includes(tag)) {
            break;
        }

        tags.push(tag);
        content = content.slice(match[0].length);
    }

    return { content, tags };
}

function replaceLeadingAudioTags(value: string, tags: readonly string[]): string {
    const { content } = splitLeadingAudioTags(value);
    const prefix = tags.map((tag) => `【${tag}】`).join("");

    return `${prefix}${content}`;
}

export function FloatingChatComposer({
    availableMediaModes = DEFAULT_AVAILABLE_MEDIA_MODES,
    defaultMediaMode = DEFAULT_COMPOSER_MEDIA_MODE,
    modelsByMediaMode = EMPTY_MODELS_BY_MEDIA_MODE,
    modelsLoading = false,
    onSend,
    placeholder = "上传参考素材并描述你想生成的内容",
}: FloatingChatComposerProps) {
    const initialMediaMode = availableMediaModes.includes(defaultMediaMode)
        ? defaultMediaMode
        : (availableMediaModes[0] ?? DEFAULT_COMPOSER_MEDIA_MODE);
    const [value, setValue] = useState("");
    const [attachments, setAttachments] = useState<ChatAttachment[]>([]);
    const [singleVideo, setSingleVideo] = useState<File | null>(null);
    const [firstFrame, setFirstFrame] = useState<File | null>(null);
    const [lastFrame, setLastFrame] = useState<File | null>(null);
    const [motionImage, setMotionImage] = useState<File | null>(null);
    const [motionVideo, setMotionVideo] = useState<File | null>(null);
    const [mediaMode, setMediaMode] = useState<ComposerMediaMode>(initialMediaMode);
    const [toolMode, setToolMode] = useState<ComposerToolMode>(getDefaultComposerToolMode(initialMediaMode));
    const [settings, setSettings] = useState<ComposerSettingsValue>(() => ({
        ...DEFAULT_COMPOSER_SETTINGS,
        resolution:
            initialMediaMode === "image" ? DEFAULT_COMPOSER_IMAGE_RESOLUTION : DEFAULT_COMPOSER_VIDEO_RESOLUTION,
    }));
    const [advancedSettings, setAdvancedSettings] = useState(DEFAULT_COMPOSER_ADVANCED_SETTINGS);
    const [audioEnabled, setAudioEnabled] = useState(false);
    const [audioSettings, setAudioSettings] = useState<ComposerAudioSettingsValue>(DEFAULT_COMPOSER_AUDIO_SETTINGS);
    const [audioTags, setAudioTags] = useState<string[]>([]);
    const [multiCamera, setMultiCamera] = useState(false);
    const [model, setModel] = useState<ComposerModelValue>("");
    const [quantity, setQuantity] = useState(1);
    const [submitting, setSubmitting] = useState(false);
    const [collapsed, setCollapsed] = useState(true);
    const [collapsedHeight, setCollapsedHeight] = useState(0);
    const [expandedHeight, setExpandedHeight] = useState(0);
    const collapsedLayerRef = useRef<HTMLDivElement>(null);
    const expandedLayerRef = useRef<HTMLDivElement>(null);
    const focusComposerOnExpandRef = useRef(false);
    const senderRef = useRef<HTMLElement>(null);
    const imageInputRef = useRef<HTMLInputElement>(null);
    const firstFrameInputRef = useRef<HTMLInputElement>(null);
    const lastFrameInputRef = useRef<HTMLInputElement>(null);
    const motionImageInputRef = useRef<HTMLInputElement>(null);
    const motionVideoInputRef = useRef<HTMLInputElement>(null);
    const referenceInputRef = useRef<HTMLInputElement>(null);
    const singleVideoInputRef = useRef<HTMLInputElement>(null);
    const videoInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const collapsedLayer = collapsedLayerRef.current;
        const expandedLayer = expandedLayerRef.current;

        if (!collapsedLayer || !expandedLayer) {
            return;
        }

        const updateHeights = () => {
            setCollapsedHeight(collapsedLayer.offsetHeight);
            setExpandedHeight(expandedLayer.offsetHeight);
        };
        const frame = requestAnimationFrame(updateHeights);
        const observer = new ResizeObserver(updateHeights);

        observer.observe(collapsedLayer);
        observer.observe(expandedLayer);

        return () => {
            cancelAnimationFrame(frame);
            observer.disconnect();
        };
    }, []);

    useEffect(() => {
        if (collapsed || !focusComposerOnExpandRef.current) {
            return;
        }

        focusComposerOnExpandRef.current = false;
        const frame = requestAnimationFrame(() => senderRef.current?.focus());

        return () => cancelAnimationFrame(frame);
    }, [collapsed]);

    const imageCount = attachments.filter((attachment) => attachment.type?.startsWith("image/")).length;
    const videoCount = attachments.filter((attachment) => attachment.type?.startsWith("video/")).length;
    const isImageMode = mediaMode === "image";
    const isAudioMode = mediaMode === "audio";
    const modelOptions = modelsByMediaMode[mediaMode] ?? EMPTY_MODEL_OPTIONS;
    const selectedModel = modelOptions.some((option) => option.id === model) ? model : (modelOptions[0]?.id ?? "");
    const isReferenceMode = toolMode === "reference";
    const isFrameMode = toolMode === "frame";
    const isEditVideoMode = mediaMode === "video" && toolMode === "edit";
    const isContinueMode = mediaMode === "video" && toolMode === "continue";
    const isMotionMode = mediaMode === "video" && toolMode === "motion";
    const isSingleVideoMode = isEditVideoMode || isContinueMode;
    const maxPromptLength = isAudioMode
        ? AUDIO_MAX_PROMPT_LENGTH
        : isImageMode
          ? IMAGE_MAX_PROMPT_LENGTH
          : VIDEO_MAX_PROMPT_LENGTH;
    const showVideoUpload =
        mediaMode === "video" &&
        toolMode !== "text" &&
        !isReferenceMode &&
        !isFrameMode &&
        !isSingleVideoMode &&
        !isMotionMode;
    const resolvedPlaceholder = isAudioMode
        ? AUDIO_PLACEHOLDER
        : isEditVideoMode
          ? EDIT_VIDEO_PLACEHOLDER
          : isContinueMode
            ? CONTINUE_PLACEHOLDER
            : isMotionMode
              ? MOTION_PLACEHOLDER
              : isFrameMode
                ? FRAME_PLACEHOLDER
                : isImageMode
                  ? IMAGE_DEFAULT_PLACEHOLDER
                  : toolMode === "text"
                    ? VIDEO_TEXT_PLACEHOLDER
                    : isReferenceMode
                      ? REFERENCE_PLACEHOLDER
                      : placeholder;

    const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(event.currentTarget.files ?? []);

        if (files.length === 0) {
            return;
        }

        setAttachments((current) => [...current, ...files.map(toAttachment)]);
        event.currentTarget.value = "";
    };

    const handleFrameFileChange = (position: "first" | "last", event: ChangeEvent<HTMLInputElement>) => {
        const file = event.currentTarget.files?.[0];

        if (!file) {
            return;
        }

        if (position === "first") {
            setFirstFrame(file);
        } else {
            setLastFrame(file);
        }

        event.currentTarget.value = "";
    };

    const handleSingleVideoChange = (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.currentTarget.files?.[0];

        if (file) {
            setSingleVideo(file);
        }

        event.currentTarget.value = "";
    };

    const handleMotionFileChange = (position: "image" | "video", event: ChangeEvent<HTMLInputElement>) => {
        const file = event.currentTarget.files?.[0];

        if (file) {
            if (position === "image") {
                setMotionImage(file);
            } else {
                setMotionVideo(file);
            }
        }

        event.currentTarget.value = "";
    };

    const resolvePromptAttachments = () => {
        if (isAudioMode) {
            return [];
        }

        if (isSingleVideoMode) {
            return singleVideo ? [toAttachment(singleVideo)] : [];
        }

        if (isMotionMode) {
            return [motionImage, motionVideo].flatMap((file) => (file ? [toAttachment(file)] : []));
        }

        if (!isFrameMode) {
            return attachments;
        }

        return [firstFrame, lastFrame].flatMap((file) => (file ? [toAttachment(file)] : []));
    };

    const clearComposerInput = () => {
        setValue("");
        setAudioTags([]);
        setAttachments([]);
        setSingleVideo(null);
        setFirstFrame(null);
        setLastFrame(null);
        setMotionImage(null);
        setMotionVideo(null);
    };

    const submitPrompt = async (promptValue: string, promptAttachments?: ChatAttachment[]) => {
        const prompt = promptValue.trim();
        const files = (promptAttachments ?? resolvePromptAttachments()).flatMap((attachment) =>
            attachment.raw ? [attachment.raw] : [],
        );

        if (
            !prompt ||
            !onSend ||
            !selectedModel ||
            isAudioMode ||
            submitting ||
            (isSingleVideoMode && !singleVideo) ||
            (isMotionMode && (!motionImage || !motionVideo))
        ) {
            return;
        }

        setSubmitting(true);
        try {
            await onSend({
                advancedSettings,
                audioEnabled,
                audioSettings,
                files,
                mediaMode,
                modelId: selectedModel,
                multiCamera,
                prompt,
                quantity,
                settings,
                toolMode,
            });
            clearComposerInput();
        } catch {
            return;
        } finally {
            setSubmitting(false);
        }
    };

    const handleSend = (event: CustomEvent<TdChatSenderParams>) => {
        void submitPrompt(event.detail.value, resolvePromptAttachments());
    };

    const updateSettings = (next: Partial<ComposerSettingsValue>) => {
        setSettings((current) => ({ ...current, ...next }));
    };

    const changeMediaMode = (nextMediaMode: ComposerMediaMode) => {
        if (mediaMode === "audio" && nextMediaMode !== "audio") {
            setValue((current) => splitLeadingAudioTags(current).content);
            setAudioTags([]);
        }

        setMediaMode(nextMediaMode);

        const nextToolMode = getComposerToolModes(nextMediaMode).some((mode) => mode === toolMode)
            ? toolMode
            : getDefaultComposerToolMode(nextMediaMode);

        setToolMode(nextToolMode);
        setSettings((current) => ({
            ...current,
            resolution:
                nextMediaMode === "image" ? DEFAULT_COMPOSER_IMAGE_RESOLUTION : DEFAULT_COMPOSER_VIDEO_RESOLUTION,
        }));

        if (nextToolMode !== "frame") {
            setFirstFrame(null);
            setLastFrame(null);
        }

        if (nextMediaMode !== "video" || (nextToolMode !== "edit" && nextToolMode !== "continue")) {
            setSingleVideo(null);
        }

        if (nextMediaMode !== "video" || nextToolMode !== "motion") {
            setMotionImage(null);
            setMotionVideo(null);
        }

        if (nextMediaMode === "image" || nextToolMode === "text") {
            setAttachments((current) => current.filter((attachment) => !attachment.type?.startsWith("video/")));
        }

        if (nextMediaMode === "audio") {
            setAttachments([]);
            setFirstFrame(null);
            setLastFrame(null);
            setSingleVideo(null);
            setMotionImage(null);
            setMotionVideo(null);
        }
    };

    const changeToolMode = (nextToolMode: ComposerToolMode) => {
        setToolMode(nextToolMode);

        if (nextToolMode !== toolMode) {
            setSingleVideo(null);
        }

        if (
            nextToolMode === "frame" ||
            nextToolMode === "edit" ||
            nextToolMode === "motion" ||
            nextToolMode === "continue"
        ) {
            setAttachments([]);
        } else {
            setFirstFrame(null);
            setLastFrame(null);
        }

        if (nextToolMode !== "motion") {
            setMotionImage(null);
            setMotionVideo(null);
        }

        if (nextToolMode === "text") {
            setAttachments((current) => current.filter((attachment) => !attachment.type?.startsWith("video/")));
        }
    };

    const changeComposerValue = (nextValue: string) => {
        setValue(nextValue);

        if (isAudioMode) {
            setAudioTags(splitLeadingAudioTags(nextValue).tags);
        }
    };

    const toggleAudioTag = (tag: string) => {
        const nextTags = audioTags.includes(tag) ? audioTags.filter((item) => item !== tag) : [...audioTags, tag];

        setAudioTags(nextTags);
        setValue((current) => replaceLeadingAudioTags(current, nextTags));
    };

    const expandComposer = () => {
        focusComposerOnExpandRef.current = true;
        setCollapsed(false);
    };

    const collapseComposer = () => {
        senderRef.current?.blur();
        setCollapsed(true);
    };

    const collapsedContentOffset = Math.max(0, expandedHeight - collapsedHeight - COMPOSER_MODE_BAR_HEIGHT);

    return (
        <section aria-label="浮动对话输入框" className={`${styles.composer} ${styles.theme}`}>
            <input accept="image/*" hidden multiple onChange={handleFileChange} ref={imageInputRef} type="file" />
            {isSingleVideoMode ? (
                <input
                    accept="video/*"
                    hidden
                    onChange={handleSingleVideoChange}
                    ref={singleVideoInputRef}
                    type="file"
                />
            ) : null}
            {isMotionMode ? (
                <>
                    <input
                        accept="image/*"
                        hidden
                        onChange={(event) => handleMotionFileChange("image", event)}
                        ref={motionImageInputRef}
                        type="file"
                    />
                    <input
                        accept="video/*"
                        hidden
                        onChange={(event) => handleMotionFileChange("video", event)}
                        ref={motionVideoInputRef}
                        type="file"
                    />
                </>
            ) : null}
            <input
                accept="image/*"
                hidden
                onChange={(event) => handleFrameFileChange("first", event)}
                ref={firstFrameInputRef}
                type="file"
            />
            <input
                accept="image/*"
                hidden
                onChange={(event) => handleFrameFileChange("last", event)}
                ref={lastFrameInputRef}
                type="file"
            />
            {isReferenceMode ? (
                <input
                    accept="image/*,video/*"
                    hidden
                    multiple
                    onChange={handleFileChange}
                    ref={referenceInputRef}
                    type="file"
                />
            ) : null}
            {showVideoUpload ? (
                <input accept="video/*" hidden multiple onChange={handleFileChange} ref={videoInputRef} type="file" />
            ) : null}
            <motion.div
                animate={collapsedHeight ? { height: collapsed ? collapsedHeight : expandedHeight } : undefined}
                className={`${styles.composerStage} ${collapsed ? styles.composerStageCollapsed : styles.composerStageExpanded}`}
                initial={false}
                transition={COMPOSER_HEIGHT_TRANSITION}
            >
                <motion.div
                    animate={{ y: collapsed ? collapsedContentOffset : 0 }}
                    className={`${styles.composerLayer} ${styles.composerExpandedLayer}`}
                    inert={collapsed}
                    initial={false}
                    ref={expandedLayerRef}
                    transition={{ ...COMPOSER_HEIGHT_TRANSITION, delay: collapsed ? 0 : 0.05 }}
                >
                    <ChatSenderWithSlots
                        actions={false}
                        attachmentsProps={{ items: attachments }}
                        autosize={{ maxRows: 8, minRows: 3 }}
                        className={styles.sender}
                        ref={senderRef}
                        footerPrefix={
                            <ComposerToolbar
                                advancedSettings={advancedSettings}
                                audioEnabled={audioEnabled}
                                audioSettings={audioSettings}
                                canSubmit={
                                    value.trim().length > 0 &&
                                    Boolean(onSend) &&
                                    Boolean(selectedModel) &&
                                    !isAudioMode &&
                                    !submitting &&
                                    (!isSingleVideoMode || singleVideo !== null) &&
                                    (!isMotionMode || (motionImage !== null && motionVideo !== null))
                                }
                                characterCount={value.length}
                                maxLength={maxPromptLength}
                                mediaMode={mediaMode}
                                model={selectedModel}
                                models={modelOptions}
                                modelsLoading={modelsLoading}
                                multiCamera={multiCamera}
                                onChangeAdvancedSettings={(next) =>
                                    setAdvancedSettings((current) => ({ ...current, ...next }))
                                }
                                onChangeAudioEnabled={setAudioEnabled}
                                onChangeAudioSettings={(next) =>
                                    setAudioSettings((current) => ({ ...current, ...next }))
                                }
                                onChangeModel={setModel}
                                onChangeMultiCamera={setMultiCamera}
                                onChangeQuantity={setQuantity}
                                onChangeSettings={updateSettings}
                                onSubmit={() => void submitPrompt(value, resolvePromptAttachments())}
                                quantity={quantity}
                                selectedAudioTags={audioTags}
                                settings={settings}
                                submitting={submitting}
                                toolMode={toolMode}
                                onToggleAudioTag={toggleAudioTag}
                            />
                        }
                        innerHeader={
                            <motion.div
                                animate={{ opacity: collapsed ? 0 : 1 }}
                                initial={false}
                                transition={{ duration: collapsed ? 0.08 : 0.12, delay: collapsed ? 0 : 0.18 }}
                            >
                                <ComposerModeBar
                                    activeMode={toolMode}
                                    mediaModes={availableMediaModes}
                                    mediaMode={mediaMode}
                                    onChangeMediaMode={changeMediaMode}
                                    onChangeMode={changeToolMode}
                                    onCollapse={collapseComposer}
                                    onReset={clearComposerInput}
                                />
                            </motion.div>
                        }
                        inputPrefix={
                            isAudioMode ? (
                                <span aria-hidden />
                            ) : isSingleVideoMode ? (
                                <ComposerSingleVideoUpload
                                    file={singleVideo}
                                    onSelect={() => singleVideoInputRef.current?.click()}
                                />
                            ) : isMotionMode ? (
                                <ComposerMotionUpload
                                    image={motionImage}
                                    onSelectImage={() => motionImageInputRef.current?.click()}
                                    onSelectVideo={() => motionVideoInputRef.current?.click()}
                                    video={motionVideo}
                                />
                            ) : isFrameMode ? (
                                <ComposerFrameUpload
                                    firstFrame={firstFrame}
                                    lastFrame={lastFrame}
                                    onSelectFirst={() => firstFrameInputRef.current?.click()}
                                    onSelectLast={() => lastFrameInputRef.current?.click()}
                                />
                            ) : (
                                <ComposerUpload
                                    imageCount={imageCount}
                                    onSelectImage={() => imageInputRef.current?.click()}
                                    onSelectReference={() => referenceInputRef.current?.click()}
                                    onSelectVideo={() => videoInputRef.current?.click()}
                                    referenceCount={attachments.length}
                                    showReference={isReferenceMode}
                                    showVideo={showVideoUpload}
                                    videoCount={videoCount}
                                />
                            )
                        }
                        onFileRemove={(event) => setAttachments(event.detail)}
                        onChange={(event) => changeComposerValue(event.detail)}
                        onSend={handleSend}
                        placeholder={resolvedPlaceholder}
                        textareaProps={{ maxlength: maxPromptLength }}
                        value={value}
                    />
                </motion.div>
                <motion.div
                    animate={{ opacity: collapsed ? 1 : 0 }}
                    className={`${styles.composerLayer} ${styles.composerCollapsedLayer}`}
                    inert={!collapsed}
                    initial={false}
                    ref={collapsedLayerRef}
                    transition={{ duration: collapsed ? 0.16 : 0.05, delay: collapsed ? 0.14 : 0 }}
                >
                    <ComposerCollapsedBar
                        characterCount={value.length}
                        maxLength={maxPromptLength}
                        mediaMode={mediaMode}
                        onExpand={expandComposer}
                        placeholder={resolvedPlaceholder}
                        toolMode={toolMode}
                        value={value}
                    />
                </motion.div>
            </motion.div>
        </section>
    );
}
