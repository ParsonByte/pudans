---
name: commit-repo
description: "Run the repository's mandatory commit flow: read the commit rule, inspect the worktree and stash, confirm scope and reuse evidence, run tests, create one single-line Conventional Commit and report it without pushing. Use when the user asks to commit or stage work."
---

# commit-repo

## Purpose

Execute `.agents/rules/commit.md` and the commit section of `.agents/rules/development-repo.md`. The output is one or more focused, title-only commits with verified scope and no stash or unrelated changes.

## Triggers

- The user asks to commit, stage or "save this work".
- A development task reaches the commit step.
- An existing commit message or staged set must be corrected.

## Steps

1. Read the commit rule before staging:
   ```bash
   cat .agents/rules/commit.md
   ```

2. Inspect the worktree and stash state:
   ```bash
   git status --short
   git branch --show-current
   git stash list
   git diff --stat
   git diff --staged --stat
   ```
   Confirm that `stash@{0}` and any other stash are unchanged. Do not pop, apply, drop or modify a stash.

3. Confirm scope:
   - Only intended paths are present.
   - No build output, local configuration, secret, lockfile noise or unrelated file is staged.
   - No other task's changes are mixed in.

4. Confirm the reuse and cleanup evidence from the `development-repo` or `reuse-check` skill:
   - Which existing implementation was reused or extended.
   - Why a new implementation was necessary, if one was created.
   - That no unused code, duplicate implementation or temporary file remains.

5. Run the gate:
   ```bash
   ./gradlew :appkernel:test
   git diff --check
   ```

6. Draft the title:
   - Form: `<type>: <subject>`.
   - Types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`, `style`.
   - Lowercase imperative subject, at most 72 characters, no trailing period.
   - A breaking change uses `!`, for example `feat!: replace the system settings route`.
   - No body, no description, no footers, no `Co-authored-by`.

7. Stage only the intended paths and commit with one `-m`:
   ```bash
   git add <path> <path>
   git commit -m "<type>: <subject>"
   ```

8. Verify the message is exactly one line and report the hash:
   ```bash
   git log -1 --pretty=%B
   git log -1 --oneline
   ```
   Do not push unless the user explicitly asks.

## Inputs and outputs

- Input: the verified change set and its task context.
- Output: one or more title-only commits, plus a report with hash, subject and verification commands.

## Constraints

- Title line only; never `git commit -m "title" -m "body"`.
- Never touch a stash without an explicit instruction.
- Never stage unrelated files, build output, secrets or local configuration.
- Never commit unless the user asked or the task explicitly includes committing.
- Never push, delete a branch, run `reset --hard` or rewrite history unless the user explicitly asks for that exact operation.
- If the message needs impact or migration detail, put it in the pull request or task summary, not in the commit body.
