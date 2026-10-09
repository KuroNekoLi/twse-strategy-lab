---
name: spring-boot-kotlin-development
description: Apply repository-compatible Kotlin, Spring Boot, persistence, and API standards.
---

# Spring Boot Kotlin Standard — TWSE Strategy Lab

Read `references/SOURCES.md` before applying these practices. The current backend is Kotlin on Java 25, Spring Boot 4.1, Maven, Spring MVC, Spring Data JPA, and H2/MySQL. Verify `backend/pom.xml`, Maven wrapper/toolchain, and configuration before framework-sensitive changes.

## Architecture and implementation

- Preserve feature-oriented packages (`api`, `backtest`, `catalog`, `market`, `paper`, `replay`, etc.) and existing public API contracts. Keep HTTP mapping in controllers, orchestration in services, pure calculations in domain code, and persistence behind repositories/store interfaces where an interchangeable boundary is real.
- Prefer constructor injection and explicit dependencies. Use interfaces at external-provider, persistence, or test seams when they enable meaningful substitution; avoid both direct vendor coupling in core services and speculative layers that only forward calls.
- Keep persistence entities internal to persistence. Map to API/domain DTOs; do not expose JPA entities as API response types. Define transaction boundaries around cohesive database work, and avoid holding a database transaction open during remote network calls.
- Keep JPA models compatible with this project's Kotlin `spring`/`jpa` compiler plugins and `-Xannotation-default-target=param-property`. Follow established entity patterns; do not convert persistence entities to Kotlin data classes or alter plugin semantics without a verified reason and tests.
- Prefer Kotlin null safety, immutable values for domain inputs/results, explicit sealed/enum states where state is constrained, and small readable functions. Do not compress complex business logic into dense one-line code. Preserve pure functions and deterministic fixtures for calculation-heavy paths.
- Use typed configuration for related settings; read credentials and deployment endpoints from environment/configuration. Never hard-code production credentials or service URLs. Do not change CORS policy without explicit scope.
- Keep persistence portable through JPA and standard query behavior when feasible. Vendor-specific SQL, schema assumptions, and database features must be isolated and documented. Follow the repository's currently authorized schema lifecycle (`ddl-auto=update`) unless a separately approved migration plan changes it.
- Remote market-data integrations must preserve source, observed/fetched time, freshness, completeness, licensing limits, and failure semantics. Never turn missing values into plausible zeroes or label delayed/historical responses as real-time.
- Coroutines do not make blocking Spring MVC/JPA work nonblocking. Use suspend/Flow only when the complete call path and persistence/provider APIs support it; do not wrap blocking JPA work in coroutine syntax or run it on event-loop threads.
- Maintain the existing MVC stack unless a scoped architecture decision justifies a migration. Check Spring Boot 4 module/artifact names and dependency management against actual resolved versions before adding dependencies.

## Verification and review

- Run `./mvnw test` (or the repository-documented Maven equivalent) in `backend/`; run package/build when packaging or plugin configuration is affected. Distinguish deterministic unit/contract tests from live provider or deployed-service verification.
- Test API compatibility, validation and error responses, persistence behavior under the configured test database, and boundary conditions. H2 success alone does not establish MySQL dialect/operational parity.
- Review transaction scope, N+1/query shape, entity/DTO boundaries, nullability, remote-call timeouts/failure handling, configuration/secrets, and licensing/data provenance where relevant.
- Report formatter/static-analysis checks only when configured and actually run. Existing repository debt belongs in the architecture audit; do not attribute it to an unchanged patch.

