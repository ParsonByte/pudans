import Highlighter from "react-highlight-words";

import { searchWords } from "../lib/model";

type HighlightedSnippetProps = {
  text: string;
  query: string;
};

export function HighlightedSnippet({ text, query }: HighlightedSnippetProps) {
  const words = searchWords(query);
  if (words.length === 0) {
    return <p className="text-sm leading-6 text-muted-foreground">{text}</p>;
  }
  return (
    <Highlighter
      className="text-sm leading-6 text-muted-foreground"
      searchWords={words}
      autoEscape
      textToHighlight={text}
      highlightClassName="rounded-sm bg-amber-200/70 text-foreground dark:bg-amber-400/30"
    />
  );
}
