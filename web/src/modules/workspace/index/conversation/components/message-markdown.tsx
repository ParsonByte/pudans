import type { ReactNode } from "react";

import {
  CodeBlock,
  CodeBlockContent,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";

type Block = { kind: "text"; value: string } | { kind: "code"; language: string; value: string };

export function MessageMarkdown({ content }: { content: string }) {
  const blocks = splitBlocks(content);
  return (
    <div className="space-y-3 text-sm leading-7">
      {blocks.map((block, index) =>
        block.kind === "code" ? (
          <CodeBlock key={index} size="sm">
            <CodeBlockHeader>
              <CodeBlockTitle>{block.language || "代码"}</CodeBlockTitle>
              <CodeBlockCopyButton value={block.value} />
            </CodeBlockHeader>
            <CodeBlockContent>{block.value}</CodeBlockContent>
          </CodeBlock>
        ) : (
          <Paragraphs key={index} text={block.value} />
        ),
      )}
    </div>
  );
}

function Paragraphs({ text }: { text: string }) {
  const paragraphs = text.split(/\n{2,}/).filter((paragraph) => paragraph.trim());
  return (
    <>
      {paragraphs.map((paragraph, index) => {
        const lines = paragraph.split("\n");
        const heading = headingLevel(lines[0]);
        if (heading) {
          const rest = lines.slice(1).join("\n").trim();
          const Tag = heading.tag;
          return (
            <div key={index} className="space-y-2">
              <Tag className={heading.className}>
                {renderInline(lines[0].replace(/^#+\s*/, ""))}
              </Tag>
              {rest ? <p className="whitespace-pre-wrap">{renderInline(rest)}</p> : null}
            </div>
          );
        }
        if (lines.every((line) => /^\s*[-*]\s+/.test(line))) {
          return (
            <ul key={index} className="list-disc space-y-1 ps-5">
              {lines.map((line, lineIndex) => (
                <li key={lineIndex}>{renderInline(line.replace(/^\s*[-*]\s+/, ""))}</li>
              ))}
            </ul>
          );
        }
        if (lines.every((line) => /^\s*\d+\.\s+/.test(line))) {
          return (
            <ol key={index} className="list-decimal space-y-1 ps-5">
              {lines.map((line, lineIndex) => (
                <li key={lineIndex}>{renderInline(line.replace(/^\s*\d+\.\s+/, ""))}</li>
              ))}
            </ol>
          );
        }
        return (
          <p key={index} className="whitespace-pre-wrap">
            {renderInline(paragraph)}
          </p>
        );
      })}
    </>
  );
}

function headingLevel(line: string): { tag: "h2" | "h3" | "h4"; className: string } | undefined {
  if (line.startsWith("# ")) {
    return { tag: "h2", className: "text-lg font-semibold" };
  }
  if (line.startsWith("## ")) {
    return { tag: "h3", className: "text-base font-semibold" };
  }
  if (line.startsWith("### ")) {
    return { tag: "h4", className: "text-sm font-semibold" };
  }
  return undefined;
}

function renderInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(text.slice(last, match.index));
    }
    const token = match[0];
    if (token.startsWith("**")) {
      nodes.push(<strong key={match.index}>{token.slice(2, -2)}</strong>);
    } else {
      nodes.push(
        <code key={match.index} className="rounded bg-muted px-1 py-0.5 font-mono text-xs">
          {token.slice(1, -1)}
        </code>,
      );
    }
    last = pattern.lastIndex;
  }
  if (last < text.length) {
    nodes.push(text.slice(last));
  }
  return nodes;
}

function splitBlocks(content: string): Block[] {
  const blocks: Block[] = [];
  const pattern = /```([^\n`]*)\n([\s\S]*?)```/g;
  let last = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(content)) !== null) {
    if (match.index > last) {
      blocks.push({ kind: "text", value: content.slice(last, match.index) });
    }
    blocks.push({
      kind: "code",
      language: match[1]?.trim() ?? "",
      value: match[2] ?? "",
    });
    last = pattern.lastIndex;
  }
  if (last < content.length) {
    blocks.push({ kind: "text", value: content.slice(last) });
  }
  return blocks;
}
