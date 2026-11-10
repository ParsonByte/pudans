import Highlighter from "react-highlight-words";
import { cn } from "../../utils/cn";
import styles from "./highlight-text.module.css";

export interface HighlightTextProps {
  text: string;
  searchWords: readonly string[];
  caseSensitive?: boolean;
  autoEscape?: boolean;
  activeIndex?: number;
  className?: string;
  highlightClassName?: string;
  /** Defaults to "mark". */
  highlightTag?: string;
}

/** Highlights search terms inside text using react-highlight-words. */
export function HighlightText({
  text,
  searchWords,
  caseSensitive = false,
  autoEscape = true,
  activeIndex,
  className,
  highlightClassName,
  highlightTag = "mark",
}: HighlightTextProps) {
  return (
    <Highlighter
      className={cn(styles.root, className)}
      highlightClassName={cn(styles.highlight, highlightClassName)}
      highlightTag={highlightTag}
      searchWords={[...searchWords]}
      textToHighlight={text}
      caseSensitive={caseSensitive}
      autoEscape={autoEscape}
      activeIndex={activeIndex}
    />
  );
}
