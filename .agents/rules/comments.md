# Comment rule

## Mandatory reading

Read this rule and `.agents/skills/comments/SKILL.md` before adding, changing or reviewing a comment. If they have not been read, do not write the comment.

## Scope

- Comments are allowed only in code files, for example `.java`.
- This rule constrains new and changed code. Do not mass-rewrite comments in untouched files; cleanup of a touched block is allowed and encouraged.
- Non-code files (config, Markdown, JSON, scripts) carry no comments unless the format itself requires them; Gradle scripts must not use double-slash comments.

## Format

- A comment is a short single-line Chinese comment, formatted exactly as `// 注释`.
- Comments are written in Chinese only; English comments are forbidden.
- Multi-line comments are forbidden: no `/* */`, no Javadoc `/** */`, no block of consecutive `//` lines.
- Double slash serves line comments only: never as a separator, decoration, disabled code, banner or any other marker.

## Content

- Comment on why, not on what the code already says.
- Avoid empty comments such as `// set value`; express intent through naming instead.
- Comments age with the code: update or delete them whenever the code changes.
- Do not leave commented-out code. Delete it; Git keeps the history.
- A TODO or FIXME is allowed only with a tracked task or issue reference and a concrete reason it cannot be resolved now.
- Do not use a comment to explain a name that could be made clearer; rename instead.
