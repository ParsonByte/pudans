# Testing rule

## Mandatory reading

Read this rule before changing behaviour in code. Behaviour changes include a new branch, a new validation, a changed response, a changed default, a changed error, a changed storage format or a changed security decision.

## Test first

- Add or update the JUnit test before or together with the implementation. A behaviour change without a test change is incomplete.
- Put the test beside the existing test package that covers the same class or endpoint. Follow the existing JUnit 5, Mockito and Reactor Test style.
- Cover the success path, the validation failure path and the compatibility path when the change touches an existing contract.
- Do not disable a test to make a change pass. If a test must be quarantined, record the reason and the tracked task in the pull request or task summary.
- If a behaviour cannot be tested with the current design, state why in the pull request or task summary and add the smallest testable seam instead of skipping the test silently.

## Commands

Run the focused test while implementing:

```bash
./gradlew :appkernel:test --tests '<fully.qualified.TestClass>'
```

Run the full backend gate before commit:

```bash
./gradlew :appkernel:test
```

If the change touches OpenAPI documentation, also run:

```bash
./gradlew :api-docs:validateOpenApiDocs
```

## Constraints

- Tests live under `appkernel/src/test/java` and mirror the production package.
- Do not add a test dependency without following `.agents/rules/tech-stack.md`.
- Do not commit failing tests, ignored tests or placeholder assertions.
- Report the exact test command and result in the task summary.
