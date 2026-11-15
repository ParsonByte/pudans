# Commit rule

## Format

Commit messages use the single-line Conventional Commits form:

```
<type>: <subject>
```

Example: `refactor: web consolidate packages and centralize api access`

Common types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `build`, `ci`, `perf`, `style`.

## Constraints

- Keep the title line only: no body, no description, no footers, no extra notes.
- Multi-paragraph messages are forbidden, including `git commit -m "title" -m "body"`.
- No `Co-authored-by`, signature blocks or change lists as trailers.
- Subject is lowercase English, imperative verb phrase, no trailing period; keep it within 72 characters.
- One commit does one thing; the subject must match the content.
