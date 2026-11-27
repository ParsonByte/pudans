"use client";

import { useState } from "react";

import Image from "next/image";
import { useRouter } from "next/navigation";

import { Layers2 } from "lucide-react";
import { MessagePlugin } from "tdesign-react";

import { consoleErrorMessage } from "@/modules/_workspace/_console/composables/_errors";
import { INDEX_PAGE_KEYS, indexPageHref } from "@/routes/(_workspace)/(_index)/paths";

import { AiChatComposer } from "../../components/ai-chat-composer";
import type { AiChatComposerVariant } from "../../components/ai-chat-composer/chat-input-types";
import { conversationTitleFromPrompt } from "../../components/conversation/conversation-utils";
import { TypewriterText } from "../../components/typewriter-text";
import {
    createConversationSession,
    startConversationRun,
    uploadConversationAssets,
} from "../../composables/_conversation";
import { CHAT_RECOMMENDATIONS, WORK_RECOMMENDATIONS } from "./composables/_dans";
import styles from "./styles/dans.module.css";

export function DansView() {
    const router = useRouter();
    const [variant, setVariant] = useState<AiChatComposerVariant>("chat");
    const [prompt, setPrompt] = useState("");
    const title = variant === "work" ? "DANS 工作，让 AI 帮你干活" : "有什么我能帮你的吗？";
    const recommendations = variant === "work" ? WORK_RECOMMENDATIONS : CHAT_RECOMMENDATIONS;
    const selectVariant = (nextVariant: AiChatComposerVariant) => {
        if (nextVariant === variant) {
            return;
        }

        setVariant(nextVariant);
        setPrompt("");
    };
    const handleSubmit = async (value: string, files: File[]) => {
        try {
            const session = await createConversationSession(conversationTitleFromPrompt(value));
            const assetIds = files.length > 0 ? await uploadConversationAssets(files) : [];
            const run = await startConversationRun({
                assetIds,
                content: value,
                sessionId: session.id,
            });

            setPrompt("");
            router.push(indexPageHref(INDEX_PAGE_KEYS.conversation, [session.id], run.id));
        } catch (error) {
            MessagePlugin.error(consoleErrorMessage(error, error instanceof Error ? error.message : "发送失败。"));
            throw error;
        }
    };

    return (
        <section aria-labelledby="dans-page-title" className={styles.page}>
            <div className={styles.content}>
                <header className={styles.hero}>
                    {variant === "work" ? (
                        <div className={styles.avatar}>
                            <Image
                                alt=""
                                aria-hidden
                                height={40}
                                priority
                                src="/brand/icon-small.svg"
                                unoptimized
                                width={40}
                            />
                        </div>
                    ) : null}
                    <h1 className={styles.title} id="dans-page-title">
                        <TypewriterText key={variant} text={title} />
                    </h1>
                    <div aria-label="交互模式" className={styles.segmented} role="group">
                        <button
                            aria-pressed={variant === "chat"}
                            className={`${styles.segment} ${variant === "chat" ? styles.segmentActive : ""}`}
                            onClick={() => selectVariant("chat")}
                            type="button"
                        >
                            对话
                        </button>
                        <button
                            aria-pressed={variant === "work"}
                            className={`${styles.segment} ${variant === "work" ? styles.segmentActive : ""}`}
                            onClick={() => selectVariant("work")}
                            type="button"
                        >
                            工作
                        </button>
                    </div>
                </header>

                <div className={styles.recommendations}>
                    <span className={styles.recommendationLabel}>为你推荐</span>
                    <div className={styles.recommendationList}>
                        {recommendations.map((recommendation) => (
                            <button
                                className={styles.recommendation}
                                key={recommendation.prompt}
                                onClick={() => setPrompt(recommendation.prompt)}
                                type="button"
                            >
                                {variant === "work" ? (
                                    <Layers2 className={styles.recommendationIcon} size={16} strokeWidth={1.8} />
                                ) : null}
                                {recommendation.title ? (
                                    <span className={styles.recommendationTitle}>{recommendation.title}</span>
                                ) : null}
                                <span className={styles.recommendationText}>{recommendation.prompt}</span>
                            </button>
                        ))}
                    </div>
                </div>

                <AiChatComposer
                    key={variant}
                    onSubmit={handleSubmit}
                    onValueChange={setPrompt}
                    value={prompt}
                    variant={variant}
                />
            </div>
        </section>
    );
}
