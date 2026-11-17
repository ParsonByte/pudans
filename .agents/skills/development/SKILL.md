---
name: development
description: "Run the repository development workflow end to end: branch from dev, implement in small commits, pass tests, then open a focused pull request with change and verification notes. Use when starting feature work, preparing a PR, changing dependencies or merging."
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
2. Check whether `main` is ahead of `dev`:
   ```bash
   git fetch origin
   git rev-list --left-right --count origin/dev...origin/main
   ```
   If and only if the first count is `0` and the second is greater than `0`, fast-forward it:
   ```bash
   git push origin refs/remotes/origin/main:refs/heads/dev
   git ls-remote origin refs/heads/dev refs/heads/main
   ```
   Verify both refs point at the same commit. If the first count is not `0`, stop and resolve the divergence. Follow "Syncing main and dev" in `.agents/rules/development.md` for the branch-protection bypass.
3. Branch from the updated integration branch before editing:
   ```bash
   git switch dev && git pull --ff-only
   git switch -c feat/audit-list
   ```
4. Implement in small, coherent changes; commit with the `commit` skill (single-line Conventional Commits).
5. Run the gate before every push:
   ```bash
   ./gradlew test
   ```
6. Scan the diff for secrets and local configuration before staging:
   ```bash
   git diff --staged | grep -Ei 'secret|token|password|api[_-]?key|BEGIN [A-Z ]*PRIVATE KEY'
   ```
7. Push the feature branch only, never main or dev, then open a pull request against `dev` using the template in `templates/pull-request.md`.
8. Request review; resolve every comment; re-run the gates after the last change before merging.
9. Feature/fix/chore PRs into `dev`: use **Rebase and merge**. `dev` into `main`: use **Create a merge commit**; then immediately fast-forward `dev` to `main` with the step 2 procedure. Never open a `main` → `dev` sync PR.

## Inputs and outputs

- Input: a task description and the affected area of the repository.
- Output: a feature branch with verified single-line commits and a focused pull request describing the change and the verification results.

## Constraints

- Never commit or push to main or dev; no direct pushes, no force-push to shared branches. The only exception is the mandatory `main` → `dev` fast-forward sync in step 2.
- Never bypass tests, and never merge with failing checks.
- Dependency changes always state the reason and the impact in the pull request.
- Secrets, tokens, certificates and local environment files never enter the repository.
- One pull request solves one problem; split unrelated work.
- Merge only after review approval.
