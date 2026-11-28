# AGENTS.md

pudans is a Java 25 / Spring Boot 4.2 microservice platform for identity, access, file storage,
notifications, messaging, jobs and search. The gateway is the only public edge; every API uses
`/apis/pudans.tolim.cn/v1alpha1/`; Nacos provides discovery and runtime configuration.

## Read before you change

1. Code, tests or configuration: `.agents/rules/development.md`,
   `.agents/rules/directory-structure.md`, `.agents/rules/tech-stack.md`,
   `.agents/rules/comments.md`, and the `development`, `reuse-check` and `testing` skills.
2. Commit: `.agents/rules/commit.md` and `.agents/skills/commit/SKILL.md`.
3. Pull request copy: `.agents/rules/pull-request.md` and
   `.agents/skills/pull-request/SKILL.md`.
4. OpenAPI: `.agents/skills/openapi/SKILL.md`.

If a task conflicts with a rule, follow the stricter rule and record the conflict in the task
summary.

## Hard rules

- Agents never push, force-push, delete remote refs, or create, edit or merge pull requests. The
  user performs every remote operation.
- Do not commit unless the user explicitly asks. Local commits are title-only Conventional
  Commits; never add a prose body or change list.
- Service versions live only in the root `VERSION` file; never hardcode an application version in
  a build file.
- Reuse first. Search the contract, communal and kernel modules before adding a class, endpoint,
  DTO, provider, configuration key or data-access method. No duplicate capability implementations.
- Do not add mock, fake, demo, seeded or stub product data.
- Runtime configuration lives in Nacos. Keep `application.yml` to bootstrap and infrastructure
  fallback; add seeds under `platform/nacos/config` and publish them.
- All cryptography uses `cn.tolim.spissatus4j`. Never add ad-hoc JCA code or another crypto library.
- Never add eiam support or Seata. eiam is a local reference source only.
- Java comments are English, single-line `//` and only in Java files. Do not add comments to SQL,
  YAML, Gradle, shell, JSON or Markdown.
- SQL changelogs live only under `sql/<service>`; every table and column needs Chinese `remarks`.
- Do not add root `/api` aliases. `pudans-contract` contains interfaces, DTOs, paths and Feign
  clients only; no implementations.
- Do not rebuild `bootJar` while a local backend is running; run local backends from a copied jar.
  Leave the user's port 3000 dev server untouched.
- Do not add module READMEs. Maintain `README.md`, `README.zh.md` and `sql/README.md`.

## Rules

| Topic | File | Summary |
| --- | --- | --- |
| Comments | `.agents/rules/comments.md` | Java-only, English, single-line `//` |
| Commits | `.agents/rules/commit.md` | Single-line Conventional Commits; no prose body |
| Pull requests | `.agents/rules/pull-request.md` | English titles and bodies; user performs remote work |
| Directory structure | `.agents/rules/directory-structure.md` | Fixed top-level modules; contracts; root SQL |
| Development | `.agents/rules/development.md` | Reuse, minimal diff, tests, Nacos, OpenAPI, safety |
| Tech stack | `.agents/rules/tech-stack.md` | Versions, dependencies, prohibited stacks |

## Skills

| Skill | Use it when |
| --- | --- |
| `development` | Starting any code, test or configuration change |
| `reuse-check` | Before creating an implementation or provider |
| `testing` | Choosing and running tests |
| `comments` | Writing or reviewing Java comments |
| `commit` | The user asks to commit |
| `pull-request` | Drafting English pull request copy |
| `openapi` | Generating or validating the OpenAPI documents |
| `skill-authoring` | Adding or changing a skill |

## Commands

```bash
./gradlew build
./gradlew test
./gradlew :api-docs:generateOpenApiDocs
./gradlew :api-docs:validateOpenApiDocs
platform/nacos/config/publish.sh
```

## Where things live

Applications are `pudans-gateway`, `pudans-iam`, `pudans-repository`, `pudans-notification`,
`pudans-mq`, `pudans-job` and `pudans-search`. Contracts are in `pudans-contract`, shared code in
`pudans-communal`, domain kernels in `pudans-kernel-modules`, infrastructure in `platform`, SQL in
`sql`, OpenAPI output in `api-docs/openapi/v3_0`, and agent material in `.agents`.

Project facts and known operational boundaries are in
`.agents/notes/project-notes.md`. Keep `AGENTS.md` short and put detail in the linked rule or skill.