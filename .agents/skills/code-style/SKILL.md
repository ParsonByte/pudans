---
name: code-style
description: Apply and verify the repository code style rules while implementing, refactoring or reviewing code. Covers formatting and lint, semantic naming, single responsibility, duplication, magic values, explicit types, error handling and dead code removal.
---

# code-style

## Purpose

Produce code that matches `.agents/rules/code-style.md` and passes the repository formatting and lint gates without suppressions.

## Triggers

- Implementing or refactoring backend Java or frontend TypeScript/React code.
- Reviewing a diff for style, duplication, naming or typing problems.
- A lint, format or type check fails.

## Steps

1. Read the local conventions: `.editorconfig`, `web/.prettierrc`, `web/eslint.config.js`, and nearby code for naming patterns.
2. Write or refactor with the checklist below in mind.
3. Run the gates for the touched side:
   ```bash
   cd web && pnpm format:check && pnpm lint && pnpm typecheck && pnpm test
   ./gradlew test            # backend
   ```
4. Fix findings in the code, never in the configuration and never with `eslint-disable` or `@SuppressWarnings`.
5. Re-run the gates until clean, then report the commands and their result.

## Checklist

| Area | Requirement |
| --- | --- |
| Formatting | Editorconfig and Prettier decide formatting; no hand reformatting, no unrelated reformat noise in a diff |
| Naming | Semantic names with purpose and unit; no `data`, `temp`, `utils2` |
| Responsibility | One job per function or component; split long or mixed-concern code |
| Duplication | Extract shared logic instead of copy-paste |
| Magic values | Numbers, strings, status codes and paths become named constants or enums |
| Types | Explicit parameters and returns; no `any` abuse, prefer `unknown` and narrow |
| Errors | Catch, log or translate and rethrow; no empty catch, no swallowed failures |
| Cleanup | No `console.log`, `System.out.println`, commented-out code, unused imports, variables or branches |
| Comments | Follow the `comments` skill and `.agents/rules/comments.md` |

## Inputs and outputs

- Input: code to write, refactor or review; failing check output when available.
- Output: compliant code plus the check commands and their results.

## Constraints

- Never weaken lint or format configuration to make code pass.
- Never add suppressions; fix the cause.
- Keep diffs focused; do not reformat untouched code.
- Code style never overrides behaviour: refactors must keep tests green.
