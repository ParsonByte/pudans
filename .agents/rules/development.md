# Development rule

## Branches

- `main` is the stable/release branch; `dev` is the integration branch.
- Branch feature, fix and chore work from `dev`; never commit or push directly to `main` or `dev`. The only exception is the mandatory fast-forward sync in "Syncing main and dev".
- Branch names are kebab-case with a type prefix, for example `feat/audit-list` or `fix/auth-redirect`.
- Hotfixes may branch from `main` only for an urgent release fix; after merging to `main`, bring the fix into `dev` through a pull request from the hotfix branch, never by merging `main` into `dev`.

## Merge strategy

- Feature/fix/chore branches into `dev`: use **Rebase and merge**. Keep commits atomic and rebase the branch on `origin/dev` before opening the PR; apart from the release sync, `dev` stays linear.
- `dev` into `main`: use **Create a merge commit**. This preserves the original `dev` commit hashes and avoids creating a second, content-identical commit history on `main`.
- Do not use **Squash** unless a branch's commits are intentionally collapsed into one.
- Do not rebase a long-lived `dev` branch onto `main`; after a merge-commit release, fast-forward `dev` to `main` as described in "Syncing main and dev".

## Syncing main and dev

- Check whether `main` is ahead of `dev` before starting new work and after every `dev` → `main` merge. Do not leave `main` ahead of `dev`.
- When `main` is ahead, fast-forward `dev` to `main` immediately. This is the only direct push allowed to `dev`.
- Run:
  ```bash
  git fetch origin
  git rev-list --left-right --count origin/dev...origin/main
  git push origin refs/remotes/origin/main:refs/heads/dev
  git ls-remote origin refs/heads/dev refs/heads/main
  ```
- The count must have `0` as its first number (no commits unique to `dev`) and a positive second number (commits unique to `main`); otherwise stop and resolve the divergence first. The two refs printed by `git ls-remote` must end at the same commit hash.
- Never sync through a `main` → `dev` pull request, merge `main` into `dev`, or force-push.
- If GitHub branch protection blocks the push, use an authorized administrator bypass for this sync only. The `dev` protection rule that forbids merge commits conflicts with the merge-commit release strategy once `main` contains a release merge; relax that protection rather than rewriting history. Stop and report if no authorized bypass exists.

## Before committing

- Tests must pass: `./gradlew test`.
- Commit messages strictly follow the commit rule (`.agents/rules/commit.md`): single-line Conventional Commits, title only.

## Merge requests

- Keep PRs small and focused: one PR solves one thing.
- State the change and the verification result (which tests ran, and their outcome).
- Dependency changes must state the reason and impact (`gradle/libs.versions.toml`).
- Configuration and secrets never enter the repository: keys, tokens, certificates and local environment config stay in environment variables or local files, and `.gitignore` must cover them.
- A PR merges only after code review, with every review comment resolved.
