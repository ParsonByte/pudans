---
name: development-repo
description: "Run the repository's mandatory development flow: read the rules, search for reusable implementations, design the smallest change, write tests first, implement, clean up redundancy and run the full gate. Use before any code, test or configuration change."
---

# development-repo

## Purpose

Execute `.agents/rules/development-repo.md`, `.agents/rules/reuse-and-cleanup.md` and `.agents/rules/testing.md` as one repeatable flow. The output is a minimal, tested, redundancy-free change with recorded reuse evidence.

## Triggers

- Starting any code, test or configuration change.
- Adding a class, function, component, configuration key or data-access method.
- Reviewing a change for missing reuse, redundancy or cleanup.
- Reaching the implementation or verification stage of a task.

## Steps

1. Read the mandatory rules before touching a file:
   ```bash
   cat .agents/rules/development-repo.md
   cat .agents/rules/reuse-and-cleanup.md
   cat .agents/rules/testing.md
   ```
   If these rules have not been read, stop; do not edit code.

2. Run the reuse check with the `reuse-check` skill. Record the commands, candidate files and the decision: reuse, extend, extract or create.

3. Define the minimal change:
   - List the files that must change and why.
   - List the files that must not change.
   - If the change crosses modules, write the reason down for the pull request or task summary.

4. Write or update the test first:
   ```bash
   ./gradlew :appkernel:test --tests '<fully.qualified.TestClass>'
   ```
   Cover the success path, the validation failure path and the compatibility path when an existing contract changes.

5. Implement the smallest change that passes the test. Reuse or extend the candidate found in step 2; do not create a parallel implementation.

6. Remove redundancy in the touched code:
   ```bash
   rg -n '<new symbol>' appkernel/src
   rg -n 'TODO|FIXME' <changed files>
   git diff --check
   ```
   Delete unused imports, fields, methods, constants, configuration keys, commented-out code and temporary files.

7. Run the full backend gate:
   ```bash
   ./gradlew :appkernel:test
   ```
   If OpenAPI documentation changed, also run:
   ```bash
   ./gradlew :api-docs:validateOpenApiDocs
   ```

8. Before committing, read `.agents/rules/commit.md` and invoke the `commit-repo` skill. Do not commit from this skill.

## Inputs and outputs

- Input: a task description, the affected area and the current worktree.
- Output: a minimal tested change, a cleanup result and a reuse evidence note for the pull request or task summary.

## Constraints

- No code change starts before the mandatory rules are read.
- No parallel implementation when an existing one can be reused or extended.
- No unrelated refactor, rename or reformat.
- No test bypass, no commented-out code and no temporary file left behind.
- Never pop, apply, drop or modify a stash without an explicit instruction.
- If a rule conflicts with the task, follow the stricter rule and record the conflict in the task summary.
