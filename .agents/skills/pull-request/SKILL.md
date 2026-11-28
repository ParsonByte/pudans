---
name: pull-request
description: Draft an English pudans pull request title and body from the actual diff, verification commands and operational impact, without pushing or opening the pull request. Use when the user asks for PR copy.
---

# pull-request

## Purpose

Produce concise, English pull request copy that follows `.agents/rules/pull-request.md` and gives the
user everything needed to push and open the request themselves.

## Triggers

- The user asks for a PR title, description or review summary.
- A development task is ready to hand to the user for a remote operation.
- An existing PR description needs restructuring or English copy.

## Steps

1. Read the diff and the task summary:
   ```bash
   git status --short
   git diff --stat
   git diff --staged --stat
   ```
2. Confirm the change is one coherent problem and tests have passed.
3. Draft an English title as `<type>: <imperative summary>`.
4. Build the body from this template:
   ```markdown
   # <type>: <imperative summary>

   ## Change

   - <What changed and why.>

   ## Verification

   - `./gradlew test` — <pass/fail>
   - `./gradlew :api-docs:validateOpenApiDocs` — <pass/fail, when relevant>

   ## Dependencies

   - <None, or the dependency, reason and impact.>

   ## Notes

   - <Risk, migration, restart boundary or follow-up.>
   ```
5. Include reuse evidence, module impact, SQL/Liquibase, Nacos dataId, restart-required keys, API
   contract changes and any skipped verification.
6. Keep the copy English and free of secrets, tokens, internal hostnames and personal data.
7. Report the title and body; do not run `git push`, `gh pr create`, `gh pr edit` or `gh pr merge`.

## Inputs and outputs

- Input: the verified diff, task context and verification results.
- Output: an English PR title and body ready for the user to publish.

## Constraints

- English only for PR copy.
- One PR solves one problem.
- Do not invent test results or verification that did not run.
- Do not open, edit, merge or push anything; the user performs every remote operation.
- Do not include secrets, credentials, local paths or unrelated changes.