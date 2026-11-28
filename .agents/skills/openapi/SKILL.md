---
name: openapi
description: Generate and validate the pudans OpenAPI 3.0 documents from the service doc profiles, verify Chinese tags and summaries, and report coverage without hand-editing JSON. Use after controller or OpenAPI annotation changes.
---

# openapi

## Purpose

Maintain `api-docs/openapi/v3_0` as a generated artifact. The generator starts each business service
with the `doc` profile, fetches its grouped document and merges the results.

## Triggers

- A controller, path, HTTP method, `@Tag`, `@Operation`, `@Parameter` or `@Schema` changes.
- The OpenAPI documents or aggregated paths must be refreshed.
- A review asks whether the generated documentation is current.

## Steps

1. Confirm the controller annotations follow `.agents/rules/comments.md` only for comments; the
   OpenAPI tags and summaries themselves are Chinese user-facing text.
2. Generate the six service documents and the aggregate:
   ```bash
   ./gradlew :api-docs:generateOpenApiDocs
   ```
   The doc profile uses ports 18091-18096 and writes `aggregated.json` plus
   `apis_<service>.v1alpha1.json` under `api-docs/openapi/v3_0`.
3. Validate the generated documents:
   ```bash
   ./gradlew :api-docs:validateOpenApiDocs
   ```
4. Inspect the result for complete coverage:
   - Every operation has a non-empty summary.
   - Every operation tag is Chinese.
   - There are no English-only tags or summaries.
   - The aggregate includes every service path.
   - The gateway has no separate group because it has no business controller.
5. Report the operation and path counts, the validation result and any remaining limitation.
6. Commit the regenerated JSON with the annotation change when the user asks to commit; never edit
   the JSON by hand.

## Inputs and outputs

- Input: controller and annotation changes.
- Output: regenerated OpenAPI 3.0 JSON and a validated coverage report.

## Constraints

- Never hand-edit `api-docs/openapi/v3_0`.
- Never start a real database or Nacos; the doc profile uses a stub datasource and disables Nacos.
- Do not leave a doc-profile process running.
- Do not add a gateway group unless the gateway gains actual controllers.
- Do not remove or rewrite Chinese tags or summaries to satisfy an English-only review.