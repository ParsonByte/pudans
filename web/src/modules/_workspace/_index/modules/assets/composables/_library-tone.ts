import styles from "../styles/assets-view.module.css";

export type LibraryTone = "brand" | "green" | "violet" | "warm";

export const LIBRARY_TONE_CLASS: Record<LibraryTone, string> = {
    brand: styles.toneBrand,
    green: styles.toneGreen,
    violet: styles.toneViolet,
    warm: styles.toneWarm,
};
