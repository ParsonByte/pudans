import Highlighter, { type HighlightTagProps } from "react-highlight-words";
import { alpha, useTheme } from "@mui/material";
import { amber } from "@mui/material/colors";

/**
 * Legacy App.tsx global ".highlight-marker": amber fill, small radius and a
 * same-colour ring (no padding). Rebuilt from MUI amber + alpha so it follows
 * the theme colour utilities instead of a raw literal.
 */
function HighlightMark({ children, className, style }: HighlightTagProps) {
  const theme = useTheme();
  return (
    <mark
      className={className}
      style={{
        backgroundColor: alpha(amber[500], 0.62),
        borderRadius: theme.spacing(0.5),
        boxShadow: "0 0 0 " + theme.spacing(0.25) + " " + alpha(amber[500], 0.62),
        color: "inherit",
        padding: 0,
        ...style,
      }}
    >
      {children}
    </mark>
  );
}

export interface HighlightTextProps {
  text: string;
  searchWords: readonly string[];
  caseSensitive?: boolean;
  autoEscape?: boolean;
  activeIndex?: number;
  className?: string;
  highlightClassName?: string;
  /** Defaults to the MUI mark matching .highlight-marker. */
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
  highlightTag,
}: HighlightTextProps) {
  return (
    <Highlighter
      className={className}
      highlightClassName={highlightClassName ?? ""}
      highlightTag={highlightClassName || highlightTag ? (highlightTag ?? "mark") : HighlightMark}
      searchWords={[...searchWords]}
      textToHighlight={text}
      caseSensitive={caseSensitive}
      autoEscape={autoEscape}
      activeIndex={activeIndex}
    />
  );
}
