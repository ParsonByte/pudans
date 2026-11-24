"use client";

import { useDreamStore } from "../../stores/use-dream-store";
import styles from "../../styles/dream-space.module.css";

export function MobileQualityNotice({ lowPower }: { lowPower: boolean }) {
    const performanceMode = useDreamStore((state) => state.performanceMode);

    if (!lowPower && !performanceMode) {
        return null;
    }

    return (
        <p className={styles.qualityNotice}>
            已自动降低粒子密度和后处理，优先保持移动端流畅度。可在设置中关闭性能优先。
        </p>
    );
}
