# Development rule

## Branches

- `main` is the only long-lived branch and the release branch.
- Branch feature, fix and chore work from the latest `origin/main`; never commit or push directly to `main`.
- Branch names are kebab-case with a type prefix, for example `feat/user-groups` or `fix/auth-redirect`.
- One branch carries one task. Finish and commit the current task before starting another branch.

## Branch base and task boundaries

- Start every feature, fix and chore branch from `origin/main`:
  ```bash
  git fetch origin
  git switch -c <type>/<name> origin/main
  ```
- Before creating a branch, `git status --short` must be empty. If a previous task still has uncommitted work, commit it or stay on the existing branch.
- `git log --oneline origin/main..HEAD` is the authoritative list of commits added by the branch.
- Delete only local branches with no unique commits. Never delete a branch that is ahead of `origin/main` without explicit instruction.

## Push and history

- The user performs every remote operation. Agents must never run `git push`, `git push --delete`, `gh pr create`, `gh pr merge`, `gh pr edit` or any command that creates, updates or deletes a remote branch, tag or pull request, even when a task appears to ask for it. Stop and ask the user to run the command.
- Never rewrite shared history (`git reset --hard` on a pushed branch, force-push or deletion of unmerged branches) unless the user explicitly asks for that exact operation.
- Pull requests are the only path into `main`.

## Merge strategy

- Feature/fix/chore branches into `main`: use **Rebase and merge**.
- Keep commits atomic and rebase the branch on `origin/main` before opening the PR; `main` stays linear.
- Do not use merge commits or squash unless a branch's commits are intentionally collapsed.

## Before committing

- Tests must pass: `./gradlew test`.
- Commit messages strictly follow the commit rule (`.agents/rules/commit.md`): single-line Conventional Commits, title only.

## Merge requests

- Keep PRs small and focused: one PR solves one thing.
- State the change and the verification result (which tests ran, and their outcome).
- Dependency changes must state the reason and impact (`gradle/libs.versions.toml`).
- Configuration and secrets never enter the repository: keys, tokens, certificates and local environment config stay in environment variables or local files, and `.gitignore` must cover them.
- A PR merges only after code review, with every review comment resolved.
