# AGENTS.md

Pudans is an enterprise identity and permission management platform. Backend: Java 25 / Spring Boot on a Gradle multi-module build. Frontend: pnpm workspace with React, served as multipage apps (auth, console, uc).

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
| `web/` | Frontend workspace root: `app/` (auth, console, uc) and `packages/` (shared, components, api-client, ui-plugin-bundler-kit) |
| `tests/web/` | Frontend Node tests |
| `scripts/web/` | Frontend structure, cycle, export and design-token checks |
| `gradle/libs.versions.toml` | Version catalog; dependency changes go here |

## Commands

```bash
./gradlew test                # backend tests
./gradlew build               # backend build

cd web
pnpm install                  # install dependencies
pnpm typecheck                # type check
pnpm lint                     # ESLint
pnpm test                     # frontend tests
pnpm verify                   # typecheck + structure checks + tests + lint + build
```

Lint, type checks and tests must pass before committing (see `.agents/rules/development.md`).

## Formatting sources

`.editorconfig` (Java, Gradle, JSON, Markdown, ...), `web/.prettierrc` and `web/eslint.config.js` are the only sources of truth for formatting and lint. Never reformat by hand and never bypass checks with disable comments.
