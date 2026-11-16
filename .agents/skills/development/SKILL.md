---
name: development
description: "Run the repository development workflow end to end: branch from main, implement in small commits, pass tests, then open a focused pull request with change and verification notes. Use when starting feature work, preparing a PR, changing dependencies or merging."
---

# development

## Purpose

Execute `.agents/rules/development.md`: isolated branches, verified commits, small reviewable pull requests and no secrets in the repository.

## Triggers

- Starting a feature, fix or refactor that will be committed or pushed.
- Preparing, updating or merging a pull request.
- Changing a dependency in `gradle/libs.versions.toml`.
- Any moment before pushing a branch.

## Steps

1. Confirm the current branch:
   ```bash
   git branch --show-current
   git remote -v
   ```
2. Branch from the main branch before editing:
   ```bash
   git switch main && git pull
   git switch -c feat/plugin-audit-list
   ```
3. Implement in small, coherent changes; commit with the `commit` skill (single-line Conventional Commits).
4. Run the gate before every push:
   ```bash
   ./gradlew test
   ```
5. Scan the diff for secrets and local configuration before staging:
   ```bash
   git diff --staged | grep -Ei 'secret|token|password|api[_-]?key|BEGIN [A-Z ]*PRIVATE KEY'
   ```
6. Push the feature branch only, never main, then open a pull request using the template in `templates/pull-request.md`.
7. Request review; resolve every comment; re-run the gates after the last change before merging.

## Inputs and outputs

- Input: a task description and the affected area of the repository.
- Output: a feature branch with verified single-line commits and a focused pull request describing the change and the verification results.

## Constraints

- Never commit or push to main; no direct pushes, no force-push to shared branches.
- Never bypass tests, and never merge with failing checks.
- Dependency changes always state the reason and the impact in the pull request.
- Secrets, tokens, certificates and local environment files never enter the repository.
- One pull request solves one problem; split unrelated work.
- Merge only after review approval.
