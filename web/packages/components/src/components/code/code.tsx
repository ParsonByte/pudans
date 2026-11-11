import {
  forwardRef,
  useCallback,
  useRef,
  useState,
  type HTMLAttributes,
  type ReactNode,
} from "react";
import { styled } from "@mui/material/styles";
import { cn } from "@pudans/utils";
import { Icon } from "../icon";
import styles from "./code.module.css";

const StyledCode = styled("code")(({ theme }) => ({
  backgroundColor: theme.palette.grey[100],
  ...theme.applyStyles("dark", { backgroundColor: theme.palette.grey[900] }),
  border: `var(--pd-border-width-thin) solid ${theme.palette.divider}`,
  borderRadius: theme.spacing(0.5),
  padding: theme.spacing(0.125, 0.5),
  fontFamily: "inherit",
}));

export interface CodeProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
}

/** Inline code token, rebuilt 1:1 from the legacy Common/Code styled(Box). */
export const Code = forwardRef<HTMLElement, CodeProps>(function Code(
  { children, className, ...props },
  ref,
) {
  return (
    <StyledCode ref={ref} className={cn(className)} {...props}>
      {children}
    </StyledCode>
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
 * The legacy project only shipped the inline Common/Code; this block keeps the
 * existing pudans API and token-driven surface for error details / API docs.
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
