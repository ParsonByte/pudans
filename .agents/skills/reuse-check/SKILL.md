---
name: reuse-check
description: "Search the repository for an existing implementation before creating a class, function, component, configuration key or data-access method, then decide whether to reuse, extend, extract or create. Use before any new implementation."
---

# reuse-check

## Purpose

Execute `.agents/rules/reuse-and-cleanup.md`: search by responsibility, naming, package path and callers, then choose reuse, extension, extraction or a documented new implementation.

## Triggers

- Before creating a class, function, component, configuration key or data-access method.
- Before adding a second implementation of a capability that may already exist.
- When a review asks whether an existing implementation could have been reused.

## Steps

1. State the responsibility in two or three keywords, not the proposed class name. Example: `mask sensitive field`, `resolve relative path`, `rotate token`.

2. Search by responsibility:
   ```bash
   rg -n -i '<keyword>' appkernel/src/main/java
   rg -n -i '<keyword>|<synonym>' appkernel/src/main/java
   ```

3. Search by naming:
   ```bash
   rg -n 'class .*<Keyword>|interface .*<Keyword>|record .*<Keyword>|enum .*<Keyword>' appkernel/src/main/java
   rg -n '<verb><Noun>|<Noun>Resolver|<Noun>Factory|<Noun>Service' appkernel/src/main/java
   ```

4. Search by package path:
   ```bash
   find appkernel/src/main/java/cn/tolim/pudans -path '*<area>*' -type f
   rg -n '^package .*<area>' appkernel/src/main/java
   ```

5. Search by callers and dependencies:
   ```bash
   rg -l '<existing symbol>' appkernel/src/main/java
   rg -n '<existing symbol>' appkernel/src/main/java
   ```

6. Search LangChain4j and LangChain4j Community before writing AI capability code:
   ```bash
   curl -sS 'https://repo1.maven.org/maven2/dev/langchain4j/' | rg -i '<capability>'
   find ~/.gradle/caches/modules-2/files-2.1/dev.langchain4j -maxdepth 2 -type d
   ```
   Inspect the matching source jar. Reuse an existing interface such as `ChatModel`, `StreamingChatModel`, `EmbeddingModel`, `ImageModel`, `ScoringModel`, `TextToSpeechModel`, `AudioTranscriptionModel` or `WebSearchEngine` when it fits, and implement only the provider adapter.

7. Apply the decision tree:
   - Same responsibility and compatible contract: reuse.
   - Same responsibility, contract too narrow: extend with an overload, parameter object or strategy.
   - Shared part only: extract the shared part and reuse it from both call sites.
   - No candidate after all four searches: create the implementation and document why.

8. Record the evidence in the pull request or task summary:
   - The commands run.
   - The candidate files inspected.
   - The LangChain4j and LangChain4j Community artifacts inspected.
   - The decision and its reason.

## Inputs and outputs

- Input: the responsibility to implement and the affected package.
- Output: a reuse decision with recorded evidence, or a documented reason for a new implementation.

## Constraints

- Do not create code before completing all four searches.
- Do not choose a new implementation because an existing one is not "exactly" right without checking an overload or extension first.
- Do not leave two implementations of the same capability.
- If a candidate is dead code, decide whether to delete it or adopt it; do not leave both.
- If no candidate exists, the new implementation must still be the smallest one that solves the stated problem.
