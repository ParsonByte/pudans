---
name: commit
description: Draft and create a single-line Conventional Commits commit when asked to commit changes, amend a commit message, or split work into commits. Enforces a title-only message with no body or footers.
---

# commit

## Purpose

Turn a set of changes into one or more commits whose messages are single-line Conventional Commits, exactly as required by `.agents/rules/commit.md`.

## Triggers

- The user asks to commit, stage, or "save this work".
- A change set needs to be split into focused commits.
- An existing commit message violates the commit rule and has to be rewritten.
- Another skill (for example `development`) reaches the commit step.

## Steps

1. Inspect the change set and branch context:
   ```bash
   git status --short
   git branch --show-current
   git diff --stat
   git diff --staged --stat
   git merge-base --is-ancestor origin/main HEAD
   git log --oneline origin/main..HEAD
   ```
2. Confirm the work is coherent, belongs to the current task and is verified. Tests must pass first; see `.agents/rules/development.md`.
3. If the change set contains multiple unrelated tasks, split it into separate commits. Do not collapse unrelated work into one commit without explicit approval.
4. Pick the type: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`, `style`.
5. Draft the subject as `<type>: <area> <lowercase imperative phrase>`, at most 72 characters, no trailing period, no capital after the type.
6. Stage only the intended paths; never stage build output, `.env`, local config or lockfile noise that does not belong to the change.
7. Commit with a single `-m`:
   ```bash
   git commit -m "refactor: web consolidate packages and centralize api access"
   ```
8. Verify the message is exactly one line:
   ```bash
   git log -1 --pretty=%B
   ```
9. Report the commit hash and the subject you used. Do not push; the user pushes unless explicitly asked.

## Examples

Good:

```
feat: add the user center authorizations view
fix: keep the console sidebar state across reloads
docs: add agent rules and skill layout
```

Bad:

```
update code                     # no type, no meaning
feat: Added Stuff.              # capitalised, trailing period
feat: add users

Body paragraph explaining why.  # body is forbidden
```

## Inputs and outputs

- Input: staged or unstaged changes, plus the intent of the change.
- Output: one or more commits, each with a single-line Conventional Commit subject; a short report with hash and subject.

## Constraints

- Title line only. No body, no description, no footers, no `Co-authored-by`, no change lists.
- Never `git commit -m "title" -m "body"`.
- One commit does one thing; split unrelated edits into separate commits.
- Never commit unless the user asked or the task explicitly includes committing.
- Never push, delete a remote branch, run `reset --hard`, `branch -D`, or rewrite history unless the user explicitly asks for that exact operation.
- Never rewrite, amend or force-push commits you do not own.
