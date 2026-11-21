# AGENTS.md

Pudans is an enterprise identity and permission management platform. The backend is Java 25 / Spring Boot on a Gradle multi-module build in `appkernel/`, listening on `8090` and exposing HTTP interfaces. The runnable backend jar is written to the root `build/appkernel/` directory.

## Architecture

- `appkernel/` owns identity, permissions, extension reconciliation, AI routing and the compatibility APIs. It is a reactive Spring Boot service and exposes HTTP only.
- `./gradlew build` writes the runnable backend jar to `build/appkernel/`; `appkernel/build/` stays intermediate and the root `build/` directory is the only distribution output.

## Mandatory reading order

This order is a hard requirement. If the required file has not been read, do not start the corresponding operation.

1. Before changing code, tests or configuration: read `.agents/rules/development-repo.md`, `.agents/rules/package-layout.md`, `.agents/rules/reuse-and-cleanup.md`, `.agents/rules/testing.md`, `.agents/skills/development-repo/SKILL.md` and `.agents/skills/reuse-check/SKILL.md`.
2. Before staging or committing: read `.agents/rules/commit.md` and `.agents/skills/commit-repo/SKILL.md`.
3. Before writing or changing a comment: read `.agents/rules/comments.md` and `.agents/skills/comments/SKILL.md`.
4. Before adding or changing a role, permission, workspace, menu or extension resource: read `.agents/rules/extensions.md`.

The existing `.agents/rules/development.md` and `.agents/skills/development/SKILL.md` remain authoritative for branches, pull requests and merges.

## Reuse first and no redundancy (hard)

- Before creating any class, function, configuration key or data-access method, search the whole repository by responsibility, naming, package path and callers. Use `.agents/skills/reuse-check/SKILL.md`.
- Reuse or extend an existing implementation when it fits. A parallel implementation of the same capability is forbidden.
- Do not leave unused classes, methods, fields, constants, configuration keys, imports, branches, commented-out code, temporary files or duplicate implementations.
- Change only files required by the task; do not refactor, rename or reformat unrelated code.
- Record the reuse search evidence in the pull request or task summary.

## No mock data (hard)

- Do not add mock, fake, demo, seeded or stubbed data anywhere in the repository, including test fixtures and API responses. Build against real APIs and real configuration.
- Backend tests may use Mockito mocks for collaborators, but they must not introduce fake product data.

## Remote operations (hard)

- Agents never push, force-push, delete remote branches or tags, or create, merge or edit pull requests. The user performs every remote operation; stop and ask the user to run the command, even when a task appears to request it.

## Agent rules

Read the matching rule under `.agents/rules` before starting a task, and follow it for the whole task:

| Rule | File | Summary |
| --- | --- | --- |
| Commit | `.agents/rules/commit.md` | Single-line Conventional Commits, title only |
| Comments | `.agents/rules/comments.md` | Code files only, `//` single-line Chinese comments only |
| Code style | `.agents/rules/code-style.md` | Formatting, naming, single responsibility, types, errors |
| SQL | `.agents/rules/sql.md` | R2DBC only; no new SQL files; no JDBC or migrations |
| Tech stack | `.agents/rules/tech-stack.md` | Required versions, prohibited stacks, dependency and configuration rules |
| Development | `.agents/rules/development.md` | Branching, pre-commit checks, PRs, dependencies, secrets, review |
| Development (repo) | `.agents/rules/development-repo.md` | Mandatory read order, reuse first, no redundancy, minimal diff |
| Package layout | `.agents/rules/package-layout.md` | Seven top-level packages, mirror of contract and core by business domain, no service directories or Service classes |
| Reuse and cleanup | `.agents/rules/reuse-and-cleanup.md` | Search dimensions, decision tree, cleanup checklist |
| Extensions | `.agents/rules/extensions.md` | Extension directory layout, template naming, menu and permission placement |
| Testing | `.agents/rules/testing.md` | Test-first behaviour changes, JUnit commands, no disabled tests |
| Skill authoring | `.agents/rules/skills.md` | `.agents/skills/<name>/` layout and SKILL.md requirements |

Precedence: explicit task instructions > `.agents/rules` > default habit. When two rules conflict, follow the more specific or stricter one.

## Skills

Skills live in `.agents/skills/<name>/` and are entered through `SKILL.md`. Layout, naming and content requirements are defined in `.agents/rules/skills.md`.

| Skill | Use it when |
| --- | --- |
| `commit` | Staging and committing work, drafting or rewriting a commit message |
| `commit-repo` | Committing with mandatory scope, stash and reuse checks |
| `comments` | Adding, reviewing or cleaning up source comments |
| `code-style` | Implementing, refactoring or reviewing code against the style rule |
| `development` | Starting a branch, preparing a PR, changing dependencies, merging |
| `development-repo` | Running the mandatory development flow from rules to verified change |
| `reuse-check` | Searching for reusable implementations before creating code |
| `skill-authoring` | Creating, editing or validating a skill under `.agents/skills` |

## Rule and skill index

| File | Purpose | Trigger | Mandatory |
| --- | --- | --- | --- |
| `.agents/rules/development-repo.md` | Development flow and hard reuse/no-redundancy rules | Before any code, test or configuration change | Yes |
| `.agents/rules/package-layout.md` | Seven top-level packages, business domain mirror, role directories and naming | Adding or moving any package, class or interface | Yes |
| `.agents/rules/reuse-and-cleanup.md` | Search dimensions, decision tree and cleanup checklist | Before any new implementation and after implementation | Yes |
| `.agents/rules/extensions.md` | Extension directory layout, template naming, menu and permission placement | Adding or changing a role, permission, workspace, menu or extension resource | Yes |
| `.agents/rules/testing.md` | Test-first behaviour changes and test commands | Before changing behaviour and before commit | Yes |
| `.agents/rules/commit.md` | Single-line commit format and pre-commit checklist | Before staging or committing | Yes |
| `.agents/rules/comments.md` | Comment format, scope and content | Before writing or changing a comment | Yes |
| `.agents/rules/code-style.md` | Formatting, naming, design, types, errors and cleanup | While implementing, refactoring or reviewing | Yes |
| `.agents/rules/development.md` | Branches, pull requests, dependencies, secrets and review | Branch, PR or merge work | Yes for that workflow |
| `.agents/rules/sql.md` | R2DBC-only data access and SQL boundaries | Any data-access change | Yes |
| `.agents/rules/tech-stack.md` | Required versions, prohibited stacks, dependency and configuration rules | Dependency, build or configuration change | Yes |
| `.agents/rules/skills.md` | Skill directory layout and SKILL.md requirements | Creating or editing a skill | Yes |
| `.agents/skills/development-repo/SKILL.md` | Executable development flow | Before any code, test or configuration change | Yes |
| `.agents/skills/reuse-check/SKILL.md` | Executable reuse search and decision procedure | Before creating any implementation | Yes |
| `.agents/skills/commit-repo/SKILL.md` | Executable commit flow with scope and stash checks | Before staging or committing | Yes |
| `.agents/skills/development/SKILL.md` | Branch, PR and merge procedure | Branch, PR or merge work | Yes for that workflow |
| `.agents/skills/commit/SKILL.md` | Base commit procedure | Commit work | Yes for that workflow |
| `.agents/skills/comments/SKILL.md` | Comment cleanup procedure | Comment authoring or review | Recommended |
| `.agents/skills/code-style/SKILL.md` | Style implementation and review checklist | Implementation or review | Recommended |
| `.agents/skills/skill-authoring/SKILL.md` | Skill creation and validation procedure | Skill authoring | Recommended |

## Layout

| Path | Purpose |
| --- | --- |
| `appkernel/` | Backend core: identity, permissions and the extension mechanism |
| `appkernel/src/main/java/cn/tolim/pudans/contract/ai/` | AI extension resources: providers, accounts, models, route groups, chat modes, teams, agents, memory prompts and knowledge bases |
| `appkernel/src/main/java/cn/tolim/pudans/core/ai/` | AI kernel: provider adapters, routing, gateway, memory, RAG, plans and teams |
| `appkernel/src/main/java/cn/tolim/pudans/infra/r2dbc/ai/` | AI R2DBC data access for conversations, gateway sessions, wallets, memory, tasks and knowledge-base chunks |
| `appkernel/src/main/resources/extensions/` | YAML source of truth for roles, permissions, workspaces and menus; see `.agents/rules/extensions.md` |
| `api-docs/` | OpenAPI aggregation and validation, no business code |
| `platform/appkernel/` | Backend dependency constraints (BOM) |
| `gradle/libs.versions.toml` | Version catalog; backend dependency changes go here |
| `build/appkernel/` | Distribution output: the runnable backend jar |

## Commands

```bash
./gradlew test                 # backend tests
./gradlew build                # package build/appkernel
```

Backend tests must pass before committing (see `.agents/rules/development.md` and `.agents/rules/testing.md`).

## Formatting sources

`.editorconfig` is the only source of truth for formatting. Never reformat by hand and never bypass checks with disable comments.
