# SQL rule

## Scope

- Applies to `appkernel` Java sources, SQL resources, Gradle build files and application configuration.
- Applies to new data access code and changes to existing data access code.

## Required

- All database access uses Spring Data R2DBC; queries remain reactive and non-blocking.
- Hand-written SQL is allowed only in the R2DBC data access layer: `infra/r2dbc/store/ReactiveExtensionStoreClientImpl`, `infra/r2dbc/store/ExtensionStoreRepository`, `infra/r2dbc/ai/**`, `infra/audit/AuditStore`, `infra/audit/AuditRecordRepository`, `infra/audit/AuditArchiveRepository` and `infra/audit/AuditArchiveGuardRepository`.
- Prefer Spring Data R2DBC repository derived queries; add custom queries to the data access layer when a derived query is not sufficient.
- Bind every value as a parameter; never insert values into SQL text by concatenation or interpolation.
- Keep SQL identifiers inside the data access layer; endpoints, services, controllers, reconcilers and filters call typed data access methods.

## Prohibited

- Do not introduce JDBC, `JdbcTemplate`, JPA, Hibernate, MyBatis, jOOQ or any blocking database access.
- Do not add Flyway, Liquibase or any database migration framework.
- Do not write SQL strings, text-block SQL or `DatabaseClient.sql(...)` in an endpoint, service, controller, reconciler or filter.
- Do not add `.sql` files, including `schema.sql`, `data.sql`, date-named migration scripts or vendor-specific schema files.
- Do not build SQL with string concatenation, template interpolation or unvalidated identifiers.

## Legacy exception

- `appkernel/src/main/resources/schema-postgresql.sql` is the only SQL resource in the repository.
- It is loaded by `spring.sql.init.schema-locations` with `spring.sql.init.mode=always` and remains the single legacy exception.
- Do not add another `.sql` file and do not extend this exception to any other path.
- Changes to `schema-postgresql.sql` are limited to idempotent schema bootstrap required by the R2DBC model, including the audit tables, AI kernel tables and triggers; no data seeding and no unrelated SQL.

## Verification

- Run `rg -n --glob '!build/**' 'DatabaseClient\\.sql|JdbcTemplate|JPA|Hibernate|MyBatis|jooq|Flyway|Liquibase' appkernel/src/main`.
- Run `find appkernel/src/main -name '*.sql'` and confirm the result is only `schema-postgresql.sql`.
- Run `./gradlew :appkernel:compileJava --console=plain` and `./gradlew :appkernel:test --console=plain`.
