# Sources and provenance

Research checked 2026-10-09. These internal rules are a project-specific synthesis; upstream sources are not copied wholesale. Recheck them against the project's resolved versions before upgrades.

- Spring Boot contributing guide: https://github.com/spring-projects/spring-boot/blob/535d133bdaa0ed46bbfb7ce578c14261806f39ac/CONTRIBUTING.adoc
- Spring Boot team practices: https://github.com/spring-projects/spring-boot/wiki/Team-Practices (wiki page; mutable)
- Spring Boot working with the code: https://github.com/spring-projects/spring-boot/wiki/Working-with-the-Code (wiki page; mutable)
- Spring PetClinic Kotlin project instructions (reference architecture, not a template to copy), checked at revision `c77f77ba5d43c2dbff7fa777c40be1d7ec321e40`: https://github.com/spring-petclinic/spring-petclinic-kotlin/blob/c77f77ba5d43c2dbff7fa777c40be1d7ec321e40/AGENTS.md
- Community Spring Boot skill considered as secondary input; Apache-2.0 metadata, used only as inspiration and not copied, checked at revision `4085c2a21e0dc6118be98044edefe72fbc84197b`: https://github.com/full-stack-skills/spring-skills/tree/4085c2a21e0dc6118be98044edefe72fbc84197b/skills/spring-boot
- Spring Data JPA skill considered as secondary input; Apache-2.0 metadata, used only as inspiration and not copied, checked at revision `4085c2a21e0dc6118be98044edefe72fbc84197b`: https://github.com/full-stack-skills/spring-skills/tree/4085c2a21e0dc6118be98044edefe72fbc84197b/skills/spring-data-jpa

Project decisions: preserve Kotlin + Java 25 + Spring MVC/JPA and current dependency management. Upstream project-specific build conventions (for example Spring JavaFormat) are not automatically this repository's requirements. Do not add Flyway/Liquibase or change database schema policy without a separate approved design.
