import { LockOnIcon, SecuredIcon } from "tdesign-icons-react";

import styles from "../styles/deep-understanding.module.css";

const PRIVACY_RULES = [
    {
        description: "默认只在当前工作区形成理解，不混用其他空间上下文。",
        title: "作用域隔离",
    },
    {
        description: "每条理解都对应明确来源，可以回到原始信号。",
        title: "证据可追溯",
    },
    {
        description: "你的反馈优先于模型推断，并可覆盖过时理解。",
        title: "修正优先",
    },
] as const;

export function PrivacyScope() {
    return (
        <section aria-labelledby="privacy-scope-title" className={styles.privacySection}>
            <span aria-hidden className={styles.privacyWatermark}>
                <SecuredIcon size={150} />
            </span>

            <div className={styles.foundationHeading}>
                <div>
                    <span className={styles.foundationEyebrow}>PRIVACY &amp; SCOPE</span>
                    <h2 id="privacy-scope-title">隐私与作用域</h2>
                </div>
                <span className={styles.privacyIcon}>
                    <LockOnIcon size={16} />
                </span>
            </div>

            <ol className={styles.privacyRules}>
                {PRIVACY_RULES.map((rule, index) => (
                    <li className={styles.privacyRule} key={rule.title}>
                        <span className={styles.ruleIndex}>0{index + 1}</span>
                        <div>
                            <strong>{rule.title}</strong>
                            <p>{rule.description}</p>
                        </div>
                    </li>
                ))}
            </ol>

            <div className={styles.privacyFooter}>
                <span aria-hidden className={styles.privacyStatusDot} />
                理解策略接口待接入
            </div>
        </section>
    );
}
