---
name: development
description: Run the pudans development workflow from mandatory rules and reuse search through implementation, tests, OpenAPI validation and a task report. Use before any code, test or configuration change.
---

# development

## Purpose

Execute `.agents/rules/development.md` and the related layout, stack and comment rules as one
repeatable flow. The output is a minimal, tested, documented change with no duplicated capability
and no remote operation.

## Triggers

- Starting a feature, fix, refactor, test or configuration change.
- Adding a controller, contract DTO, Feign client, provider, configuration key or SQL changelog.
- Preparing the change for the user to commit or open a pull request.

## Steps

1. Read the mandatory rules:
   `.agents/rules/development.md`, `.agents/rules/directory-structure.md`,
   `.agents/rules/tech-stack.md` and `.agents/rules/comments.md`.
2. Inspect the worktree and branch before editing:
   ```bash
   git status --short
   git branch --show-current
   ```
3. Run the reuse search with the `reuse-check` skill. Record the candidates and the reuse,
   extension, extraction or creation decision.
4. Define the smallest change and the files that must not change. If the change crosses modules,
   state why in the task summary.
5. Add or update the test first for a behaviour change; use the `testing` skill.
6. Implement in the owning module. Keep contracts, providers, paths and DTOs in sync. Do not add a
   second implementation of an existing capability.
7. Remove unused imports, fields, methods, configuration keys, temporary files and touched
   commented-out code.
8. Run the gates:
   ```bash
   ./gradlew test
   ./gradlew :api-docs:validateOpenApiDocs
   ```
   When controllers or OpenAPI annotations change, run
   `./gradlew :api-docs:generateOpenApiDocs` before validation. When Nacos seed configuration
   changes, run `platform/nacos/config/publish.sh` only when a local Nacos instance is available.
9. Report the changed files, reuse evidence, commands, results, OpenAPI/Nacos/SQL impact and any
   restart or migration boundary. Do not commit unless the user asks; follow the `commit` skill.

## Inputs and outputs

- Input: the task, the current worktree and the affected module.
- Output: a minimal tested change, cleanup evidence, verification results and a task report.

## Constraints

- Never push, open a pull request, force-push or mutate remote state.
- Never add mock, fake, demo, seeded or stub product data.
- Never bypass tests or leave disabled tests.
- Never add static environment-specific runtime configuration; use Nacos and keep only bootstrap
  and infrastructure fallback in `application.yml`.
- All cryptography uses `cn.tolim.spissatus4j`; never add ad-hoc crypto.
- Never add eiam support or Seata.
- Keep SQL under `sql/<service>` and preserve Liquibase identity.
- Java comments only, English, single-line `//`; no comments in non-Java files.
- Do not rebuild `bootJar` while a local backend is running; leave port 3000 alone.