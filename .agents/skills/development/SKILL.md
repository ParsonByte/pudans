---
name: development
description: "Run the repository development workflow end to end: branch from main, implement in small commits, pass tests, then prepare the branch and pull request copy for the user to push and open. Use when starting feature work, preparing a PR, changing dependencies or merging."
---

# development

## Purpose

Execute `.agents/rules/development.md`: isolated branches, verified commits, small reviewable pull requests and no secrets in the repository.

## Triggers

- Starting a feature, fix or refactor that will be committed; the user performs every push.
- Preparing pull request copy for the user; the user opens, updates and merges it.
- Changing a dependency in `gradle/libs.versions.toml`.
- Any moment before handing a branch to the user.

## Steps

1. Confirm the current branch:
   ```bash
   git branch --show-current
   git remote -v
   ```
2. Branch from `main` before editing:
   ```bash
   git switch main && git pull --ff-only
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
6. Do not push and do not open the pull request. Report the branch name, the PR title and the PR body (from `templates/pull-request.md`) so the user performs the remote operation.
7. Resolve review comments locally when the user relays them, and re-run the gates after the last change; the user merges.
8. Record the merge strategy for the user: feature/fix/chore PRs into `main` use **Rebase and merge**, and `main` stays linear.

## Inputs and outputs

- Input: a task description and the affected area of the repository.
- Output: a feature branch with verified single-line commits and a focused pull request describing the change and the verification results.

## Constraints

- Agents never push, force-push, delete remote branches or tags, or create, merge or edit pull requests; the user performs every remote operation.
- Never commit directly to `main`; work on a feature branch and hand it to the user.
- Never bypass tests, and never merge with failing checks.
- Dependency changes always state the reason and the impact in the pull request.
- Secrets, tokens, certificates and local environment files never enter the repository.
- One pull request solves one problem; split unrelated work.
- Merge only after review approval.
