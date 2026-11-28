---
name: commit
description: Draft and create a single-line Conventional Commit when the user explicitly asks to commit or stage pudans changes. Enforces scope checks, tests and a title-only subject without pushing.
---

# commit

## Purpose

Turn a verified change set into focused commits with single-line Conventional Commit subjects, as
required by `.agents/rules/commit.md`.

## Triggers

- The user explicitly asks to commit, stage or save work.
- A change set must be split into focused commits.
- An existing commit subject violates the commit rule.

## Steps

1. Inspect the worktree and branch:
   ```bash
   git status --short
   git branch --show-current
   git diff --stat
   git diff --staged --stat
   git stash list
   ```
2. Confirm the work belongs to the current task, tests pass and OpenAPI was regenerated and
   validated when controllers or annotations changed.
3. Split unrelated work into separate commits. Do not stage build output, generated files that
   changed unintentionally, local configuration, secrets or unrelated noise.
4. Choose a type: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf` or
   `style`. Use `!` after the type for a breaking change.
5. Draft `<type>: <lowercase imperative subject>` at most 72 characters, with no trailing period and
   no prose body or change list.
6. Stage only the intended paths and commit with one subject:
   ```bash
   git add <path> <path>
   git commit -m "<type>: <subject>"
   ```
7. Verify the subject:
   ```bash
   git log -1 --pretty=%B
   ```
8. Report the commit hash and subject. Do not push; the user performs every remote operation.

## Inputs and outputs

- Input: a verified change set and the task context.
- Output: one or more title-only Conventional Commits and a short report.

## Constraints

- Title-only subject; no body, change list or extra trailers. If the execution environment requires
  an attribution trailer, keep it outside the subject and add no other trailers.
- One commit does one coherent thing.
- Never commit unless the user explicitly asks.
- Never push, open a pull request or rewrite history.
- Never touch a stash without an explicit instruction.
- Never stage secrets, local environment files or build output.