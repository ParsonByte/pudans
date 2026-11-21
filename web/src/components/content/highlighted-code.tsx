"use client";

import { CodeBlockContent, CodeBlockLine } from "@/components/ui/code-block";
import { useHighlightedCode } from "@/hooks/use-highlighted-code";

type HighlightedCodeProps = {
  code: string;
  language: string;
};

export function HighlightedCode({ code, language }: HighlightedCodeProps) {
  const lines = useHighlightedCode(code, language);

  return (
    <CodeBlockContent>
      {lines.map((line, index) => (
        <CodeBlockLine key={index}>{line}</CodeBlockLine>
      ))}
    </CodeBlockContent>
  );
}
