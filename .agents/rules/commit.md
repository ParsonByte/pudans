# Commit rule

## Mandatory reading

Read this rule and `.agents/skills/commit-repo/SKILL.md` before staging or committing. If they have not been read, do not commit.

## Format

Commit messages use the single-line Conventional Commits form:

```
<type>: <subject>
```

Example: `refactor: web consolidate packages and centralize api access`

Common types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`, `style`.

## Constraints

- Keep the title line only: no body, no description, no footers, no extra notes.
- Multi-paragraph messages are forbidden, including `git commit -m "title" -m "body"`.
- No `Co-authored-by`, signature blocks or change lists as trailers.
- Subject is lowercase English, imperative verb phrase, no trailing period; keep it within 72 characters.
- One commit does one thing; the subject must match the content.
- A breaking change uses the Conventional Commits breaking marker in the title, for example `feat!: replace the system settings route`. The migration details belong in the pull request or task summary, never in the commit body.

## Pre-commit checklist

Before staging, verify all of the following:

- The change is coherent and belongs to the current task.
- A reuse search was run and its evidence is recorded; no existing implementation was ignored. See `.agents/rules/reuse-and-cleanup.md`.
- The change leaves no unused code, duplicate implementation, temporary file or commented-out block.
- `git status --short` contains only the intended paths; build output, local configuration, secrets and unrelated files are not staged.
- `git stash list` was checked and no stash was popped, applied, dropped or modified without an explicit instruction.
- Tests pass; see `.agents/rules/testing.md` and `.agents/rules/development.md`.
- The diff has no whitespace errors: `git diff --check`.

## Impact and migration

The repository commit format is title-only, so impact, migration notes and breaking-change details cannot be commit-body paragraphs. Record them in the pull request description or the task summary:

- What changed and which modules are affected.
- Whether a migration runs automatically, and what an operator must do manually.
- Which existing contracts are deprecated or removed, and their replacement.
- The exact verification commands and results.
