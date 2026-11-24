import type { ReactNode } from "react";

import { ChatBubbleIcon, DataBaseIcon, LightbulbIcon, LinkIcon, RefreshIcon } from "tdesign-icons-react";

import styles from "../styles/deep-understanding.module.css";

type UnderstandingStep = {
    description: string;
    icon: ReactNode;
    title: string;
};

const UNDERSTANDING_STEPS: readonly UnderstandingStep[] = [
    {
        description: "接收对话、创作与反馈信号。",
        icon: <ChatBubbleIcon size={16} />,
        title: "输入",
    },
    {
        description: "定位可追溯的上下文来源。",
        icon: <LinkIcon size={16} />,
        title: "证据",
    },
    {
        description: "组织成可解释的理解判断。",
        icon: <LightbulbIcon size={16} />,
        title: "理解",
    },
    {
        description: "让修正覆盖过时判断。",
        icon: <RefreshIcon size={16} />,
        title: "反馈",
    },
    {
        description: "沉淀为长期可复用的认知。",
        icon: <DataBaseIcon size={16} />,
        title: "长期记忆",
    },
];

export function UnderstandingFlow() {
    return (
        <section aria-labelledby="understanding-flow-title" className={styles.flowSection}>
            <div className={styles.foundationHeading}>
                <div>
                    <span className={styles.foundationEyebrow}>HOW IT WORKS</span>
                    <h2 id="understanding-flow-title">理解如何形成</h2>
                </div>
                <span className={styles.foundationBadge}>五阶段链路</span>
            </div>
            <p className={styles.foundationDescription}>每一次理解都由证据驱动，并允许你随时修正。</p>

            <ol className={styles.flowSteps}>
                {UNDERSTANDING_STEPS.map((step, index) => (
                    <li className={styles.flowStep} key={step.title}>
                        <div className={styles.stepMarker}>
                            <span className={styles.stepNumber}>0{index + 1}</span>
                            <span className={styles.stepIcon}>{step.icon}</span>
                        </div>
                        <h3>{step.title}</h3>
                        <p>{step.description}</p>
                    </li>
                ))}
            </ol>

            <div className={styles.flowFootnote}>
                <span aria-hidden className={styles.footnoteLine} />
                理解不是一次生成，而是持续的校准。
            </div>
        </section>
    );
}
