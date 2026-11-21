"use client";

import { type ReactNode, isValidElement } from "react";
import ReactMarkdown, { type Components } from "react-markdown";

import remarkGfm from "remark-gfm";

import {
  CodeBlock,
  CodeBlockCopyButton,
  CodeBlockHeader,
  CodeBlockTitle,
} from "@/components/ui/code-block";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

import { HighlightedCode } from "./highlighted-code";

type MarkdownContentProps = {
  content: string;
  className?: string;
};

type CodeBlockInfo = {
  language: string;
  value: string;
};

const LANGUAGE_PATTERN = /language-([\w-]+)/;

const markdownComponents: Components = {
  h1: ({ children }) => <h2 className="text-lg font-semibold">{children}</h2>,
  h2: ({ children }) => <h2 className="text-lg font-semibold">{children}</h2>,
  h3: ({ children }) => <h3 className="text-base font-semibold">{children}</h3>,
  h4: ({ children }) => <h4 className="text-sm font-semibold">{children}</h4>,
  h5: ({ children }) => <h5 className="text-sm font-semibold">{children}</h5>,
  h6: ({ children }) => <h6 className="text-sm font-semibold">{children}</h6>,
  p: ({ children }) => <p className="wrap-break-word whitespace-pre-wrap">{children}</p>,
  a: ({ children, href, title }) => (
    <a
      href={href}
      title={title}
      target={href?.startsWith("http") ? "_blank" : undefined}
      rel={href?.startsWith("http") ? "noreferrer" : undefined}
      className="font-medium text-primary underline underline-offset-2"
    >
      {children}
    </a>
  ),
  ul: ({ children }) => <ul className="list-disc space-y-1 ps-5">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal space-y-1 ps-5">{children}</ol>,
  li: ({ children }) => <li className="min-w-0">{children}</li>,
  blockquote: ({ children }) => (
    <blockquote className="border-s-2 border-border ps-4 text-muted-foreground">
      {children}
    </blockquote>
  ),
  hr: () => <hr className="border-border" />,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  del: ({ children }) => <del className="text-muted-foreground line-through">{children}</del>,
  img: ({ alt, src }) => (
    <img
      src={typeof src === "string" ? src : undefined}
      alt={alt ?? ""}
      className="max-h-96 max-w-full border object-contain"
    />
  ),
  table: ({ children }) => <Table>{children}</Table>,
  thead: ({ children }) => <TableHeader>{children}</TableHeader>,
  tbody: ({ children }) => <TableBody>{children}</TableBody>,
  tfoot: ({ children }) => <TableFooter>{children}</TableFooter>,
  tr: ({ children }) => <TableRow>{children}</TableRow>,
  th: ({ children }) => <TableHead>{children}</TableHead>,
  td: ({ children }) => <TableCell className="whitespace-normal">{children}</TableCell>,
  caption: ({ children }) => <TableCaption>{children}</TableCaption>,
  input: ({ checked, type }) => (
    <input
      type={type}
      checked={Boolean(checked)}
      readOnly
      className="me-1.5 align-middle"
      tabIndex={-1}
    />
  ),
  pre: ({ children }) => {
    const block = codeBlockOf(children);
    if (!block) {
      return (
        <pre className="overflow-x-auto border bg-muted/30 p-4 font-mono text-xs">{children}</pre>
      );
    }

    return (
      <CodeBlock size="sm">
        <CodeBlockHeader>
          <CodeBlockTitle>{block.language || "代码"}</CodeBlockTitle>
          <CodeBlockCopyButton value={block.value} />
        </CodeBlockHeader>
        <HighlightedCode code={block.value} language={block.language} />
      </CodeBlock>
    );
  },
  code: ({ children, className }) => (
    <code className={cn("bg-muted px-1 py-0.5 font-mono text-[0.85em]", className)}>
      {children}
    </code>
  ),
};

function codeBlockOf(children: ReactNode): CodeBlockInfo | null {
  if (!isValidElement(children) || children.type !== "code") {
    return null;
  }
  const props = children.props as { children?: ReactNode; className?: string };
  return {
    language: LANGUAGE_PATTERN.exec(props.className ?? "")?.[1] ?? "",
    value: textOf(props.children),
  };
}

function textOf(node: ReactNode): string {
  if (typeof node === "string" || typeof node === "number") {
    return String(node);
  }
  if (Array.isArray(node)) {
    return node.map(textOf).join("");
  }
  if (isValidElement<{ children?: ReactNode }>(node)) {
    return textOf(node.props.children);
  }
  return "";
}

export function MarkdownContent({ className, content }: MarkdownContentProps) {
  return (
    <div data-motion="enter" className={cn("min-w-0 text-sm leading-7 [&>*+*]:mt-3", className)}>
      <ReactMarkdown components={markdownComponents} remarkPlugins={[remarkGfm]}>
        {content}
      </ReactMarkdown>
    </div>
  );
}
