# Development rule

## Start here

Before changing code, tests, configuration or SQL, read:

- `.agents/rules/directory-structure.md`
- `.agents/rules/tech-stack.md`
- `.agents/rules/comments.md`
- `.agents/skills/development/SKILL.md`
- `.agents/skills/reuse-check/SKILL.md`
- `.agents/skills/testing/SKILL.md`

Before committing or drafting a pull request, read `.agents/rules/commit.md` and
`.agents/rules/pull-request.md`.

## Workflow

1. Inspect the current branch and worktree; do not disturb unrelated work.
2. Search the repository for an existing implementation before adding a class, endpoint, DTO,
   configuration key or provider. Prefer reuse, an extension or an extraction.
3. Define the smallest change and the files that must not change.
4. Add or update the test first when behaviour changes.
5. Implement in the owning module; keep contracts and providers in sync.
6. Remove unused code, duplicate providers, stale configuration keys, temporary files and
   commented-out code in the touched area.
7. Run the gate; fix the cause rather than suppressing it.
8. Regenerate OpenAPI when controllers or annotations change.
9. Report the commands, results, risks and any restart/migration boundary.

## Branching and remote operations

- `main` is the long-lived branch. Work on a focused feature, fix or chore branch when the user asks
  for branch/commit work.
- Agents never push, force-push, delete remote branches or tags, open or edit a pull request, or
  merge anything. The user performs every remote operation.
- Never rewrite shared history or touch a stash without an explicit instruction.
- Do not commit unless the user explicitly asks; then follow `.agents/rules/commit.md`.

## Project constraints

- All public APIs use `/apis/pudans.tolim.cn/v1alpha1/`; do not add root `/api` aliases.
- Do not add a service without a contract module and a host application.
- Keep `pudans-contract` implementation-free and keep provider implementations in their service.
- All runtime configuration flows through Nacos; do not hard-code environment-specific values.
  Keep `application.yml` for bootstrap and infrastructure fallback only.
- All encryption and decryption uses `cn.tolim.spissatus4j`. Do not add ad-hoc crypto, direct JCA
  usage or another crypto library.
- Never add eiam support to this repository. eiam is a local reference source only.
- Never add Seata or distributed-transaction dependencies.
- Use PostgreSQL only. Add DB changes under `sql/<service>`; never in module resources.
- Do not add mock, fake, demo or seeded business data. Tests may mock collaborators but must not
  invent product data.
- Java comments are English single-line `//`; do not add comments to non-Java files.
- Do not regenerate `bootJar` while a local backend is running. Run local backends from a copied
  jar so a rebuild cannot corrupt the running artifact.
- Leave the user's development server on port 3000 untouched.
- Do not add a module README; only the root READMEs and `sql/README.md` are maintained.
- Service and shared versions live only in the root `VERSION` file; never hardcode a service
  version in a build file.

## Gates

```bash
./gradlew test
./gradlew :api-docs:validateOpenApiDocs
```

When controllers, OpenAPI annotations or API paths change:

```bash
./gradlew :api-docs:generateOpenApiDocs
./gradlew :api-docs:validateOpenApiDocs
```

When Nacos seed configuration changes:

```bash
platform/nacos/config/publish.sh
```

Tests disable Nacos discovery and config. The IAM Testcontainers tests require Docker. Report any
test that could not run and why; never disable a failing test silently.

## Reporting

Report the change, the commands that ran, the pass/fail result, reuse evidence, OpenAPI/Nacos/SQL
impact and any residual risk. Do not claim a remote operation happened; the user owns it.