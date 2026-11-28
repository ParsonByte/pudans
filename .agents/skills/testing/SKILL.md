---
name: testing
description: Select and run pudans unit, context, Testcontainers and OpenAPI verification for a change, without contacting Nacos and without disabling failing tests. Use while implementing and before handing work to the user.
---

# testing

## Purpose

Execute the testing and verification expectations in `.agents/rules/development.md`. The output is
evidence that the changed behaviour and the full project still pass.

## Triggers

- A behaviour, validation, response, error, security decision, configuration key or SQL changes.
- Before reporting a task as complete.
- Before asking the user to commit or open a pull request.

## Steps

1. Read `.agents/rules/development.md` and the test conventions in the owning module.
2. Add or update the JUnit test before or with the implementation. Cover the success path, the
   validation failure path and the compatibility path when a contract changes.
3. Run the focused test while implementing:
   ```bash
   ./gradlew :pudans-<service>:test --tests '<fully.qualified.TestClass>'
   ```
4. Run the full gate before reporting:
   ```bash
   ./gradlew test
   ```
   Tests disable Nacos discovery and config. IAM PostgreSQL/Redis tests use Testcontainers and
   require Docker.
5. When controllers, OpenAPI annotations or API paths change, regenerate and validate:
   ```bash
   ./gradlew :api-docs:generateOpenApiDocs
   ./gradlew :api-docs:validateOpenApiDocs
   ```
6. When Nacos seed configuration changes, validate the YAML and publish it only against a local
   Nacos instance:
   ```bash
   platform/nacos/config/publish.sh
   ```
7. Report the exact command, the test count, failures/errors/skips and any test that could not run.
   Never report a passing result that was not observed.

## Inputs and outputs

- Input: the changed behaviour and the affected service or module.
- Output: added or updated tests, a full gate result and a verification report.

## Constraints

- Never disable, ignore, skip or weaken a failing test to make a change pass.
- Do not add a test dependency without updating `gradle/libs.versions.toml`.
- Do not use mock, fake, demo or stub product data; mocks are only for collaborators.
- Do not contact a real Nacos server from tests.
- Do not leave a service running after a verification step.
- If a test cannot run because of the environment, report the reason and the command; do not claim
  success.