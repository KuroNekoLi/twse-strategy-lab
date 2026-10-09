# Kotlin backend baseline

The backend uses Kotlin 2.3.21 on Java 25 with Spring Boot 4.1.1. Spring Boot manages the Kotlin, Jackson 3 Kotlin module, and coroutine dependency versions. Maven compiles production and test code from `src/main/kotlin` and `src/test/kotlin`; Spring and JPA compiler plugins provide proxyable Spring beans and JPA-compatible entity construction.

Spring MVC and Spring Data JPA remain the request and persistence stacks. Existing HTTP routes, JSON fields, validation behavior, calculation rules, and relational mappings are the compatibility boundary for this language migration. Coroutines Core and Reactor integration are available for future work. Current market-data clients and JPA repositories are blocking, so current handlers do not claim non-blocking behavior by being marked `suspend`, and no endpoint is modeled as `Flow` without a streaming source and consumer that need it.

The Java runtime target is 25 across Maven, CI, and the Zeabur Docker image. The application jar remains `target/twse-strategy-lab-api-1.0.0.jar`, copied to `/app/app.jar` by the Docker build.
