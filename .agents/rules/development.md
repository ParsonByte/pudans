# Development rule

## Branches

- Branch off the main branch; never commit or push directly to main.
- Branch names are kebab-case with a type prefix, for example `feat/console-audit-list` or `fix/auth-redirect`.

## Before committing

- Tests must pass: `./gradlew test`.
- Commit messages strictly follow the commit rule (`.agents/rules/commit.md`): single-line Conventional Commits, title only.

## Merge requests

- Keep PRs small and focused: one PR solves one thing.
- State the change and the verification result (which tests ran, and their outcome).
- Dependency changes must state the reason and impact (`gradle/libs.versions.toml`).
- Configuration and secrets never enter the repository: keys, tokens, certificates and local environment config stay in environment variables or local files, and `.gitignore` must cover them.
- A PR merges only after code review, with every review comment resolved.
