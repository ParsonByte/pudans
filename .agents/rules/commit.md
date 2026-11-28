# Commit rule

## Mandatory reading

Read this rule and `.agents/skills/commit/SKILL.md` before staging or committing. If they have
not been read, do not commit. Agents commit only when the user explicitly asks.

## Format

Commit messages use a single-line Conventional Commits subject:

```text
<type>: <subject>
<type>!: <subject>
```

The `!` immediately after the type marks a breaking change. Common types are `feat`, `fix`,
`refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf` and `style`.

Examples:

```text
feat: add notification provider retry controls
fix: keep the IAM session cookie on gateway redirects
refactor: consolidate repository storage providers
docs: add the Nacos configuration guide
```

## Constraints

- The subject is lowercase English, imperative and at most 72 characters; do not end it with a
  period.
- One commit does one coherent thing. Split unrelated edits into separate commits.
- Do not add a prose body, change list or extra trailers. If the execution environment appends a
  mandatory attribution trailer, keep it outside the subject and do not add other trailers.
- Do not put migration or impact detail in the commit; put it in the pull request or task summary.
- Stage only intended paths. Never stage build output, generated OpenAPI JSON changes that were not
  regenerated intentionally, local configuration, secrets or lockfile noise.
- Never commit unless the user asked. Never push, open a pull request, or change any remote ref.

## Pre-commit checklist

- The change is coherent and belongs to the current task.
- Reuse was checked and documented; no duplicate implementation was introduced.
- Tests pass: `./gradlew test`.
- OpenAPI was regenerated and validated when controller or annotation files changed:
  `./gradlew :api-docs:generateOpenApiDocs` and
  `./gradlew :api-docs:validateOpenApiDocs`.
- `git status --short` contains only the intended paths.
- No secret, token, password, certificate or local environment file is staged.
- `git diff --check` passes.
- No stash is popped, applied, dropped or modified without an explicit instruction.
- No generated build output or temporary file remains in the change set.