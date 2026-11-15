# Code style rule

## Formatting and static checks

- Follow the project formatting and lint configuration: `.editorconfig`, `web/.prettierrc`, `web/eslint.config.js`.
- Do not reformat existing code by hand and do not bypass checks with `eslint-disable`, `@SuppressWarnings` or similar.

## Design

- Name things semantically: names carry purpose and unit; no `data`, `temp`, `utils2` or other meaningless names.
- Functions and components keep a single responsibility; split code that grows long or mixes concerns.
- Avoid duplication: extract shared logic into functions, components or utilities. Avoid magic values: numbers, strings, status codes and paths become named constants or enums.

## Types and errors

- Types are explicit: annotate public APIs, parameters and return values. TypeScript must not abuse `any`; prefer `unknown` with narrowing.
- Handle errors explicitly: log or translate and rethrow after catching. Empty catches and swallowed errors are forbidden.

## Cleanup

- No debug output in commits (`console.log`, `System.out.println`, temporary breakpoints).
- No dead or inert code: delete commented-out code and unused imports, variables, functions and branches.
- Comments strictly follow the comment rule (`.agents/rules/comments.md`).
