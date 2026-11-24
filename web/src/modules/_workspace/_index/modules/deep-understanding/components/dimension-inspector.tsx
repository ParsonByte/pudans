import { FingerprintIcon, LinkIcon, LockOnIcon } from "tdesign-icons-react";

import type { UnderstandingDimension } from "../composables/_dimensions";
import styles from "../styles/deep-understanding.module.css";

type DimensionInspectorProps = {
    dimension: UnderstandingDimension;
    index: number;
    total: number;
};

export function DimensionInspector({ dimension, index, total }: DimensionInspectorProps) {
    const ordinal = String(index + 1).padStart(2, "0");
    const totalLabel = String(total).padStart(2, "0");

    return (
        <aside aria-labelledby="dimension-inspector-title" className={styles.inspector}>
            <div className={styles.inspectorContent} key={dimension.id}>
                <div className={styles.inspectorMeta}>
                    <span>当前维度</span>
                    <span className={styles.dimensionOrdinal}>
                        {ordinal} <i>/ {totalLabel}</i>
                    </span>
                </div>

                <div className={styles.inspectorHeading}>
                    <span className={styles.inspectorIcon}>{dimension.icon}</span>
                    <div>
                        <h2 id="dimension-inspector-title">{dimension.label}</h2>
                        <span className={styles.inspectorKicker}>UNDERSTANDING DIMENSION</span>
                    </div>
                </div>

                <p className={styles.inspectorDescription}>{dimension.description}</p>

                <div className={styles.sectionDivider} />

                <div className={styles.sectionHeading}>
                    <h3>理解信号</h3>
                    <span>待采集</span>
                </div>
                <ul className={styles.sourceList}>
                    {dimension.sources.map((source) => (
                        <li className={styles.sourceItem} key={source}>
                            <span className={styles.sourceIcon}>
                                <LinkIcon size={13} />
                            </span>
                            <span>{source}</span>
                            <span className={styles.sourceState}>待接入</span>
                        </li>
                    ))}
                </ul>

                <div className={styles.factGrid}>
                    <div className={styles.fact}>
                        <span>状态</span>
                        <strong>等待首次理解</strong>
                    </div>
                    <div className={styles.fact}>
                        <span>作用域</span>
                        <strong>当前工作区</strong>
                    </div>
                    <div className={styles.fact}>
                        <span>更新</span>
                        <strong>随反馈校准</strong>
                    </div>
                </div>

                <div className={styles.inspectorNotice}>
                    <span className={styles.noticeIcon}>
                        <FingerprintIcon size={17} />
                    </span>
                    <div>
                        <strong>理解接口待接入</strong>
                        <span>接入后展示证据、置信度与最近更新。</span>
                    </div>
                </div>

                <div className={styles.inspectorScope}>
                    <LockOnIcon size={13} />
                    仅使用当前工作区授权内容
                </div>
            </div>
        </aside>
    );
}
