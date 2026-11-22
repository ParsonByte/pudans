---
name: comments
description: "Add, rewrite or clean up source comments so they follow the strict comment rule: code files only, one short Chinese line per comment, double slash only. Use when writing or reviewing code that contains comments, Javadoc or block comments."
---

# comments

## Purpose

Keep comments in the codebase minimal and compliant: short single-line Chinese comments introduced by `//`, and nothing else.

## Triggers

- Writing or editing a source file, such as `.java`, `.ts` or `.tsx`, that needs an explanatory comment.
- Reviewing a diff that adds block comments, Javadoc, banners or commented-out code.
- A review note asks for "a comment" and the correct form must be chosen.

## Steps

1. Restrict the work to code files. Config, Markdown, JSON and shell files get no comments.
2. Decide whether a comment is needed at all. If naming can carry the meaning, rename instead of commenting.
3. Write the comment as one short Chinese line directly above the statement it explains, indented with the code:
   ```java
   // 保持重试预算低于网关超时。
   var maxAttempts = 3;
   ```
4. Remove violations in the touched files:
   - `/* ... */` block comments and Javadoc `/** ... */`.
   - Blocks of consecutive `//` lines.
   - Banners, separators and decoration made of slashes, such as `// ----`.
   - Commented-out code.
5. Check the touched files:
   ```bash
   rg -n '/\*' --glob '*.{java,ts,tsx,js,jsx}' .
   awk 'prev ~ /^[[:space:]]*\/\// && $0 ~ /^[[:space:]]*\/\// { print FILENAME": "NR } { prev = $0 }' $(git ls-files --modified --others --exclude-standard -- '*.java' '*.ts' '*.tsx' '*.js' '*.jsx')
   ```
6. Report which files gained or lost comments and why each remaining comment earns its place.

## Inputs and outputs

- Input: source files, or a diff that needs comment review.
- Output: an edited diff where every comment is a single `//` Chinese line, plus a short list of removed violations.

## Constraints

- Chinese only, one line only, format exactly `// 注释`.
- The rule is immutable: never edit `.agents/rules/comments.md` or add an exception elsewhere to allow another comment form.
- English comments are forbidden.
- No `/* */`, no Javadoc, no consecutive `//` blocks, no multi-line wording.
- Double slash is never used for separators, banners, decoration or disabled code.
- Comments explain why, not what; delete comments that only restate the code.
- Never add comments to non-code files to satisfy a request.
