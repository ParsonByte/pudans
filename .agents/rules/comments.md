# Comment rule

## Scope

- Comments are allowed only in Java source files.
- Never add comments to SQL, XML, YAML, JSON, Gradle, shell, properties or Markdown files.
- Existing comments outside Java are not permission to add more; do not copy them into new files.
- This rule applies to new and changed code. Do not mass-rewrite untouched files.

## Format

- The only permitted form is a short English single-line comment introduced by `//`.
- Write the comment directly above the statement, field, method or class it explains.
- Do not use `/* ... */`, Javadoc `/** ... */`, consecutive `//` blocks, banners, separators,
  disabled code or decorative slash runs.
- Keep one idea per comment. If the idea needs a paragraph, improve the code or move the
  explanation to a task summary or note.

## Content

- Explain why the code exists, the constraint it preserves or the failure it prevents.
- Do not restate what a clear name already says.
- Update or delete a comment when the code changes; stale comments are defects.
- Rename code instead of adding a comment that explains a poor name.
- Do not leave commented-out code. Delete it; Git retains the history.
- A `TODO` or `FIXME` is allowed only when it names a tracked task and a concrete reason the work
  cannot be completed now.
- Do not add generated, boilerplate or author-tag comments.

## Review

For touched Java files, scan for forbidden forms:

```bash
rg -n '/\*|^\s*//.*\n\s*//' --glob '*.java' <touched paths>
```

For the diff, confirm that no non-Java file gained a comment:

```bash
git diff -- '*.sql' '*.xml' '*.yml' '*.yaml' '*.json' '*.gradle' '*.sh' '*.md'
```

Report any removed violations and why each remaining comment earns its place.