# Pull request rule

## Language

Pull request titles and descriptions are always written in English, even when the task discussion
is in another language. Keep the title specific and the description concise.

## Scope

- One pull request solves one problem. Split unrelated work.
- The title follows the commit style: `<type>: <imperative summary>`.
- The description must state what changed, why, which modules are affected and how the change was
  verified.
- Include the exact test, OpenAPI validation and configuration publication commands that ran and
  their outcomes.
- Record the reuse evidence: the search performed, the existing implementation reused or extended,
  and why a new implementation was necessary if one was added.
- State dependency changes, their reason and their impact. Version changes belong in
  `gradle/libs.versions.toml`.
- State operational impact: SQL/Liquibase migration, Nacos dataId changes, restart-required keys,
  contract or API changes, and any follow-up work.
- Include risks and reviewer focus areas. Do not include secrets, tokens, internal hostnames or
  personal data.

## Template

```markdown
# <type>: <imperative summary>

## Change

- <What changed and why.>

## Verification

- `./gradlew test` — <pass/fail>
- `./gradlew :api-docs:validateOpenApiDocs` — <pass/fail, when relevant>

## Dependencies

- <None, or the dependency, reason and impact.>

## Notes

- <Risk, migration, restart boundary or follow-up.>
```

## Remote operations

Agents never run `git push`, `git push --delete`, `gh pr create`, `gh pr merge`, `gh pr edit` or any
command that creates, updates, merges or deletes a remote branch, tag or pull request. The user
performs every remote operation. Draft the title and body and stop.