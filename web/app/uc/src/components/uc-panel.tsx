import type { ReactNode } from "react";
import styles from "./uc-panel.module.less";

export function UcPanel({ children }: { children: ReactNode }) {
  return <div className={styles.panel}>{children}</div>;
}
