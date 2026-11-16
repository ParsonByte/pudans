# Repository development rule

## Mandatory reading

Read this rule, `.agents/rules/reuse-and-cleanup.md`, `.agents/rules/testing.md` and `.agents/skills/development-repo/SKILL.md` before changing code, tests or configuration. If these files have not been read, do not start the change.

Before a commit, read `.agents/rules/commit.md` and `.agents/skills/commit-repo/SKILL.md`. Before writing or changing a comment, read `.agents/rules/comments.md`.

The branch, pull-request and merge workflow remains in `.agents/rules/development.md` and `.agents/skills/development/SKILL.md`.

## Reuse first (hard)

Before creating any class, function, component, configuration key or data-access method, search the whole repository for an existing implementation with the same or a close responsibility:

```bash
rg -n -i '<responsibility keyword>' appkernel/src/main/java
rg -n 'class .*<Keyword>|interface .*<Keyword>|record .*<Keyword>|enum .*<Keyword>' appkernel/src/main/java
rg -l '<existing symbol>' appkernel/src/main/java
find appkernel/src/main/java -path '*<area>*' -type f
```

Also inspect the package that owns the closest responsibility, the callers of similar methods, and the adjacent modules before deciding to create a new implementation.

Apply this order:

1. An existing type or method already has the same responsibility and a compatible contract: reuse it.
2. The responsibility matches but the existing contract is too narrow: extend the existing type or method with an overload, a parameter object or a strategy.
3. Only part of the logic matches: extract the shared part and reuse it from both call sites.
4. No candidate exists: create the new implementation and record the search commands, candidate files and the reason reuse was not possible in the pull request or task summary.

Parallel implementations are forbidden. Examples: a second permission check, a second date or number formatter, a second HTTP client wrapper, a second JSON mapper, a second configuration loader, a second secret cipher, a second path resolver. A new implementation that duplicates an existing one is a defect, not a style preference.

## No redundancy (hard)

A change must not leave any of the following behind:

- Unused classes, methods, fields, constants, configuration keys, imports or branches.
- Commented-out code, debug output, temporary files or sample output.
- A second implementation of an existing capability.
- Duplicate configuration keys, duplicate constants or copy-pasted blocks that should be shared.
- A TODO or FIXME without a tracked task or issue reference.

After the implementation, run:

```bash
rg -n '<new symbol>' appkernel/src
git diff --check
./gradlew :appkernel:compileJava
```

Remove everything the search reports as unused or duplicated. Do not leave cleanup for a later task.

## Minimal diff (hard)

- Change only files directly required by the task. Do not refactor, rename or reformat unrelated code.
- Do not reformat untouched lines; `.editorconfig` defines formatting.
- A cross-module change must state in the pull request or task summary why the second module is required.
- Prefer the smallest change that solves the stated problem; a broader redesign needs explicit approval.

## Change safety

- Sensitive values such as keys, salts, passwords and tokens are never written to logs. Responses expose only a configured/not-configured state or a mask.
- Breaking an existing API or storage contract requires a compatibility path: keep the old route, key or field as a deprecated alias with a migration, and document the migration.
- Do not add a dependency, build change or configuration change outside the scope of the task.
- Behaviour changes require a test change; see `.agents/rules/testing.md`.

## Verification

Before reporting the change as complete, run:

```bash
./gradlew :appkernel:test
git diff --check
```

Report the exact commands and their results. If a check cannot run, state which check was skipped and why.
