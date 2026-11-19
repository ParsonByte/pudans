# Contributing to Pudans

Thanks for taking the time to contribute. This document explains the expected workflow; the detailed, authoritative rules are in [AGENTS.md](AGENTS.md) and [`.agents/rules`](.agents/rules).

## Before you start

- Read [AGENTS.md](AGENTS.md), then the relevant rules and skills under [`.agents`](.agents).
- For non-trivial changes, open an issue or discussion first so the approach and scope can be agreed.
- Search for an existing implementation before creating a class, component, method or configuration key. Reuse or extend it, following the `reuse-and-cleanup` rule.
- Do not add mock, fake, demo, seeded or stubbed data anywhere in the repository. Build against real APIs and configuration.

## Branching

- `main` is the only long-lived branch and the release branch.
- Create a short-lived branch from the latest `origin/main`: `<type>/<name>`, for example `feat/ai-route-groups` or `fix/chat-stream`.
- Never commit directly to `main`.
- One branch carries one task. Rebase on `origin/main` before opening a pull request.
- Do not push directly to `main` or rewrite shared history. Push your feature branch and open a pull request.

## Commits

- Use the single-line Conventional Commits format: `<type>: <subject>`.
- The subject is lowercase English, imperative, at most 72 characters and has no trailing period.
- Keep one commit to one logical change.
- Do not add a body, footers or `Co-authored-by` trailers to commits.

Examples:

```text
feat: add ai route group editor
fix: persist ai message metadata and decode jsonb
docs: add readme contributing and license
```

## Code standards

- Follow [`.editorconfig`](.editorconfig). Do not reformat unrelated code.
- Comments are single-line Chinese `//` comments only; do not add Javadoc blocks, commented-out code or English comments.
- Backend: WebFlux, R2DBC and the existing Jackson setup only. Do not add JDBC, JPA, Spring MVC, Flyway or a second HTTP or JSON library.
- Frontend: React 19, TypeScript, TanStack Router/Query, Tailwind CSS v4 and shadcn/ui. Add shadcn components only through `npx shadcn@latest add <component>`; do not hand-write files under `web/src/components/ui`.
- Dependencies: declare backend versions in `gradle/libs.versions.toml`; declare frontend dependencies in `web/package.json` and lock them with npm. State the purpose and overlap in the pull request.
- Never commit secrets, tokens, private keys or local environment files.

## Tests and checks

Run the relevant checks before pushing:

```bash
./gradlew :appkernel:test
./gradlew :api-docs:validateOpenApiDocs
```

```bash
cd web
npm run lint
npm run format:check
npm run typecheck
```

`make verify` runs the backend gate and `git diff --check`.

Add or update tests with every behaviour change. Backend tests live under `appkernel/src/test/java` and use JUnit 5, Mockito and Reactor Test. Do not disable tests to make a change pass.

## Pull requests

- Keep pull requests small and focused on one problem.
- Write the title and description in English.
- Describe what changed, which modules are affected, how it was verified and any migration or manual steps.
- Include the reuse search evidence required by `.agents/rules/reuse-and-cleanup.md` when you add an implementation.
- A pull request merges only after review and with all review comments resolved. Use rebase and merge to keep `main` linear.

## License

By contributing, you agree that your contribution is licensed under the repository's [GNU General Public License v3.0](LICENSE), and that you have the right to submit it under that license.
