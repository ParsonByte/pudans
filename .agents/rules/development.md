# Development rule

## Branches

- `main` is the stable/release branch; `dev` is the integration branch.
- Branch feature, fix and chore work from `dev`; never commit or push directly to `main` or `dev`.
- Branch names are kebab-case with a type prefix, for example `feat/plugin-audit-list` or `fix/auth-redirect`.
- Hotfixes may branch from `main` only for an urgent release fix; after merging to `main`, merge the fix back into `dev`.

## Merge strategy

- Feature/fix/chore branches into `dev`: use **Rebase and merge**. Keep commits atomic and rebase the branch on `origin/dev` before opening the PR; `dev` stays linear.
- `dev` into `main`: use **Create a merge commit**. This preserves the original `dev` commit hashes and avoids creating a second, content-identical commit history on `main`.
- Do not use **Squash** unless a branch's commits are intentionally collapsed into one.
- Do not rebase a long-lived `dev` branch onto `main`; after a merge-commit release, fast-forward `dev` to `main` instead.

## Before committing

- Tests must pass: `./gradlew test`.
- Commit messages strictly follow the commit rule (`.agents/rules/commit.md`): single-line Conventional Commits, title only.

## Merge requests

- Keep PRs small and focused: one PR solves one thing.
- State the change and the verification result (which tests ran, and their outcome).
- Dependency changes must state the reason and impact (`gradle/libs.versions.toml`).
- Configuration and secrets never enter the repository: keys, tokens, certificates and local environment config stay in environment variables or local files, and `.gitignore` must cover them.
- A PR merges only after code review, with every review comment resolved.
