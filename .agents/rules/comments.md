# Comment rule

## Scope

- Comments are allowed only in code files, for example `.java`, `.ts` or `.tsx`.
- Non-code files (config, Markdown, JSON, scripts) carry no comments unless the format itself requires them; Gradle scripts must not use double-slash comments.

## Format

- A comment is a short single-line English comment, formatted exactly as `// comment`.
- Multi-line comments are forbidden: no `/* */`, no Javadoc `/** */`, no block of consecutive `//` lines.
- Double slash serves line comments only: never as a separator, decoration, disabled code, banner or any other marker.

## Content

- Comment on why, not on what the code already says.
- Avoid empty comments such as `// set value`; express intent through naming instead.
- Comments age with the code: update or delete them whenever the code changes.
