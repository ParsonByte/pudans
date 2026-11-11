import {
  forwardRef,
  useCallback,
  useRef,
  useState,
  type HTMLAttributes,
  type ReactNode,
} from "react";
import { cn } from "../../utils/cn";
import { Icon } from "../icon";
import styles from "./code.module.css";

export interface CodeProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
}

/** Inline code token, migrated from the legacy Common/Code component. */
export const Code = forwardRef<HTMLElement, CodeProps>(function Code(
  { children, className, ...props },
  ref,
) {
  return (
    <code ref={ref} className={cn(styles.inline, className)} {...props}>
      {children}
    </code>
  );
});

Code.displayName = "Code";

export interface CodeBlockProps extends Omit<HTMLAttributes<HTMLPreElement>, "children"> {
  code?: string;
  children?: ReactNode;
  language?: string;
  showCopy?: boolean;
  copyLabel?: string;
  copiedLabel?: string;
  wrap?: boolean;
  maxHeight?: number | string;
  showLineNumbers?: boolean;
}

async function copyText(value: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return true;
    }
  } catch {
    // fall through to the legacy path
  }
  try {
    const area = document.createElement("textarea");
    area.value = value;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    document.execCommand("copy");
    document.body.removeChild(area);
    return true;
  } catch {
    return false;
  }
}

/**
 * Multi-line code surface with optional language label and copy affordance.
 * Used by error details, API docs and future editor surfaces.
 */
export const CodeBlock = forwardRef<HTMLPreElement, CodeBlockProps>(function CodeBlock(
  {
    code,
    children,
    language,
    showCopy = true,
    copyLabel = "Copy",
    copiedLabel = "Copied",
    wrap = false,
    maxHeight,
    showLineNumbers = false,
    className,
    style,
    ...props
  },
  ref,
) {
  const [copied, setCopied] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const text = code ?? (typeof children === "string" ? children : "");

  const handleCopy = useCallback(async () => {
    if (!(await copyText(text))) {
      return;
    }
    setCopied(true);
    if (timer.current) {
      clearTimeout(timer.current);
    }
    timer.current = setTimeout(() => setCopied(false), 1600);
  }, [text]);

  const lines = showLineNumbers ? text.split("\n") : [];

  return (
    <div className={cn(styles.block, className)}>
      {language || (showCopy && text) ? (
        <div className={styles.toolbar}>
          <span className={styles.language}>{language}</span>
          {showCopy && text ? (
            <button type="button" className={styles.copy} onClick={handleCopy}>
              <Icon name={copied ? "cr:Checkmark" : "cr:CopyOutlined"} size={14} />
              <span>{copied ? copiedLabel : copyLabel}</span>
            </button>
          ) : null}
        </div>
      ) : null}
      <pre
        ref={ref}
        className={cn(styles.pre, wrap && styles.wrap)}
        style={{ maxHeight, ...style }}
        {...props}
      >
        {showLineNumbers && lines.length > 0 ? (
          <span className={styles.lines} aria-hidden="true">
            {lines.map((_, index) => (
              <span key={index}>{index + 1}</span>
            ))}
          </span>
        ) : null}
        <code className={styles.code}>{children ?? code}</code>
      </pre>
    </div>
  );
});

CodeBlock.displayName = "CodeBlock";
