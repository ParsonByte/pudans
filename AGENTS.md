# AGENTS.md

Pudans is an enterprise identity and permission management platform. Backend: Java 25 / Spring Boot on a Gradle multi-module build.

## Agent rules

Read the matching rule under `.agents/rules` before starting a task, and follow it for the whole task:

| Rule | File | Summary |
| --- | --- | --- |
| Commit | `.agents/rules/commit.md` | Single-line Conventional Commits, title only |
| Comments | `.agents/rules/comments.md` | Code files only, `//` single-line English comments only |
| Code style | `.agents/rules/code-style.md` | Formatting, naming, single responsibility, types, errors |
| Development | `.agents/rules/development.md` | Branching, pre-commit checks, PRs, dependencies, secrets, review |
| Skill authoring | `.agents/rules/skills.md` | `.agents/skills/<name>/` layout and SKILL.md requirements |

Precedence: explicit task instructions > `.agents/rules` > default habit. When two rules conflict, follow the more specific or stricter one.

## Skills

Skills live in `.agents/skills/<name>/` and are entered through `SKILL.md`. Layout, naming and content requirements are defined in `.agents/rules/skills.md`.

| Skill | Use it when |
| --- | --- |
| `commit` | Staging and committing work, drafting or rewriting a commit message |
| `comments` | Adding, reviewing or cleaning up source comments |
| `code-style` | Implementing, refactoring or reviewing code against the style rule |
| `development` | Starting a branch, preparing a PR, changing dependencies, merging |
| `skill-authoring` | Creating, editing or validating a skill under `.agents/skills` |

## Layout

| Path | Purpose |
| --- | --- |
| `appkernel/` | Backend core: identity, permissions and the extension mechanism |
| `api-docs/` | OpenAPI aggregation and validation, no business code |
| `platform/appkernel/` | Backend dependency constraints (BOM) |
| `platform/plugin/` | Plugin dependency constraints (BOM) |
| `gradle/libs.versions.toml` | Version catalog; dependency changes go here |

## Commands

```bash
./gradlew test                # backend tests
./gradlew build               # backend build
```

Tests must pass before committing (see `.agents/rules/development.md`).

## Formatting sources

`.editorconfig` is the only source of truth for formatting. Never reformat by hand and never bypass checks with disable comments.
