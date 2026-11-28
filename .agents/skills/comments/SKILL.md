---
name: comments
description: Add, rewrite or validate source comments in pudans so every comment is an English single-line double-slash comment in a Java file. Use when writing or reviewing comments, Javadoc, block comments or non-Java config comments.
---

# comments

## Purpose

Keep comments compliant with `.agents/rules/comments.md`: Java-only, English, single-line `//`
comments that explain why the code exists.

## Triggers

- Writing or editing Java code that needs an explanatory comment.
- Reviewing a diff that adds block comments, Javadoc, banners, commented-out code or comments in
  YAML, SQL, Gradle, shell or Markdown.
- Deciding whether a comment is necessary at all.

## Steps

1. Decide whether naming can carry the meaning. If it can, rename instead of commenting.
2. Restrict comments to Java source files. Do not add comments to SQL, XML, YAML, JSON, Gradle,
   shell, properties or Markdown.
3. Write one short English line directly above the statement, field, method or class it explains:
   ```java
   // Keep retry attempts below the gateway timeout.
   var maxAttempts = 3;
   ```
4. Remove violations in touched Java files: `/* ... */`, Javadoc, consecutive `//` blocks, banners,
   separators, decorative slashes and commented-out code.
5. Scan the touched Java files and the non-Java diff:
   ```bash
   rg -n '/\*' --glob '*.java' <touched paths>
   git diff -- '*.sql' '*.xml' '*.yml' '*.yaml' '*.json' '*.gradle' '*.sh' '*.md'
   ```
6. Report the comments added or removed and why each remaining comment earns its place.

## Inputs and outputs

- Input: Java sources or a diff that needs comment review.
- Output: compliant comments and a list of removed violations.

## Constraints

- English only, one line only, `//` only.
- No Javadoc, block comments, consecutive `//` blocks or decorative slash runs.
- Comments explain why, not what; delete comments that restate the code.
- Never add comments to non-Java files to satisfy a request.
- Do not mass-rewrite comments in untouched files.