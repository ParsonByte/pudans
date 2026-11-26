# Reuse and cleanup rule

## Mandatory reading

Read this rule before creating a class, function, component, configuration key or data-access method, and again before reporting the change as complete. It is a hard rule, not a suggestion.

The development workflow is in `.agents/rules/development-repo.md` and `.agents/skills/development-repo/SKILL.md`. The executable search procedure is in `.agents/skills/reuse-check/SKILL.md`.

## Reuse search dimensions

Search all four dimensions before deciding that a new implementation is needed.

### 1. Responsibility

Search for the behaviour, not only the proposed name:

```bash
rg -n -i '<responsibility keyword>' appkernel/src/main/java
rg -n -i '<responsibility keyword>|related synonym' appkernel/src/main/java
```

Examples of responsibility keywords: `permission`, `authorize`, `format`, `cache`, `cipher`, `webhook`, `retry`, `pagination`, `path`, `resolve`, `mask`.

### 2. Naming

Search the naming vocabulary the repository already uses:

```bash
rg -n 'class .*<Keyword>|interface .*<Keyword>|record .*<Keyword>|enum .*<Keyword>' appkernel/src/main/java
rg -n '<verb><Noun>|<noun>Resolver|<noun>Factory|<noun>Service' appkernel/src/main/java
```

### 3. Package path

Search the package that owns the closest responsibility and its neighbours:

```bash
find appkernel/src/main/java/cn/tolim/pudans -path '*<area>*' -type f
rg -n '^package .*<area>' appkernel/src/main/java
```

### 4. Callers and dependencies

Find who already calls the existing candidate, and who would call the new implementation:

```bash
rg -l '<existing symbol>' appkernel/src/main/java
rg -n '<existing symbol>' appkernel/src/main/java
```

If an existing candidate has callers, reuse or extend it instead of creating a parallel type. If the candidate is unused today, decide whether it is dead code to delete or a reusable abstraction to adopt; do not leave both.

### 5. AI library capabilities

Before implementing any AI capability in Java, search LangChain4j core and LangChain4j Community for an existing interface or implementation:

```bash
curl -sS 'https://repo1.maven.org/maven2/dev/langchain4j/' | rg -i '<capability>'
find ~/.gradle/caches/modules-2/files-2.1/dev.langchain4j -maxdepth 2 -type d
```

Inspect the matching source jar under `~/.gradle/caches/modules-2/files-2.1/dev.langchain4j/<artifact>/<version>/`. Reuse a LangChain4j interface such as `ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ImageModel`, `ScoringModel`, `TextToSpeechModel`, `AudioTranscriptionModel` or `WebSearchEngine` when it fits, and implement only the provider-specific adapter. A new generic abstraction is allowed only when neither LangChain4j nor LangChain4j Community provides a compatible contract; record the artifacts inspected and the reason in the task summary.

## Decision tree

1. **Same responsibility and compatible contract** — reuse the existing type or method. Change the new call site, not the existing implementation.
2. **Same responsibility, contract too narrow** — extend the existing type or method with an overload, an optional parameter object or a strategy interface.
3. **Partially overlapping responsibility** — extract the shared part into a named function or component, then reuse it from both existing and new call sites.
4. **No candidate after all four searches** — create a new implementation. Record the search commands, the candidates inspected and the reason reuse was not possible in the pull request or task summary.

Do not choose option 4 because the existing implementation is "not exactly what I need" without first checking whether an overload or a small extension is enough.

## Redundancy criteria

A change contains redundancy when it has any of the following:

- Two implementations of the same capability with different names.
- A new class or method that wraps an existing one without adding behaviour.
- An exported class, method, field or constant with no caller or reference.
- A configuration key, constant or default value duplicated in two places.
- Copy-pasted blocks that differ only by a parameter.
- Commented-out code, debug output, temporary files or sample output.
- A TODO or FIXME with no tracked task or issue reference.

## Cleanup checklist

Run this checklist after implementation and before commit:

```bash
rg -n '<new symbol>' appkernel/src
rg -n 'TODO|FIXME' <changed files>
git diff --check
./gradlew :appkernel:compileJava
./gradlew :appkernel:test
```

Remove every unused import, field, method, constant, configuration key and branch. Delete commented-out code and temporary files. If a TODO must remain, link it to a tracked task in the same line or in the pull request and explain why it cannot be resolved now.

## Required evidence

The pull request or task summary must include:

- The reuse search commands that were run.
- The candidate files inspected.
- The decision: reused, extended, extracted or newly created.
- If newly created, the reason no existing implementation could be reused.
