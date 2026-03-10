# Technology Stack

**Analysis Date:** 2026-03-09

## Languages

**Primary:**
- Java 17 - Backend application (Spring Boot)
- JavaScript (ES modules) - Frontend (Vue 3 / Quasar)

**Secondary:**
- SQL - Database migrations via Flyway (`src/main/resources/db/migration/`)
- XML - Logback config (`src/main/resources/config/logback-spring.xml`)

## Runtime

**Environment:**
- JVM (Java 17), managed by Maven wrapper (`mvnw` / `mvnw.cmd`)
- Node.js v22.16.0 (managed by `frontend-maven-plugin` during build)

**Package Manager:**
- Maven (backend) - `pom.xml` with Spring Boot parent 3.5.11
- npm 11.4.2 (frontend) - `src/frontend/package-lock.json` lockfile present

## Frameworks

**Core:**
- Spring Boot 3.5.11 - Backend application framework (`pom.xml`)
- Spring Cloud 2025.0.1 - Cloud abstractions and circuit breaker (`pom.xml`)
- Spring Security - Authentication and authorization (`spring-boot-starter-security`)
- Spring Data JPA + Hibernate - ORM (`spring-boot-starter-data-jpa`)
- Spring Boot Mail - Email sending (`spring-boot-starter-mail`)
- Spring Boot Actuator - Metrics and health endpoints (`spring-boot-starter-actuator`)
- Spring Boot Cache - Caching abstraction (`spring-boot-starter-cache`)
- Spring Boot AOP - Aspect-oriented programming for cross-cutting concerns (`spring-boot-starter-aop`)
- Thymeleaf - Server-side templating for email templates (`spring-boot-starter-thymeleaf`)
- Vue 3.5.22 - Frontend reactive framework (`src/frontend/package-lock.json`)
- Quasar 2.16.0 - Vue UI framework / component library (`src/frontend/package-lock.json`)

**Testing:**
- Spring Boot Test - Integration testing (`spring-boot-starter-test`)
- Testcontainers + PostgreSQL - Containerized DB for tests (`testcontainers/postgresql`)
- Spring Security Test - Security context in tests (`spring-security-test`)
- Mockito - Unit test mocking (`mockito-core`)
- AssertJ - Fluent assertions (`assertj-core` 3.24.2)
- Instancio - Test data generation (`instancio-core` 2.10.0)
- Awaitility - Async test assertions (`awaitility` 4.2.0)
- json-unit-assertj - JSON assertions (`json-unit-assertj` 4.1.0)
- guava-testlib - Google Guava test utilities (test scope)
- datasource-proxy + sql-table-name-parser - SQL query inspection in tests

**Build/Dev:**
- Maven Failsafe Plugin - Integration test execution (`maven-failsafe-plugin`)
- frontend-maven-plugin 1.15.1 - Builds frontend inside Maven lifecycle
- @quasar/app-vite 2.1.0 - Vite-based Quasar build tool
- Vite - Frontend bundler (via Quasar app-vite)
- ESLint 9.14.0 + eslint-plugin-vue - Frontend linting
- Prettier 3.3.3 - Frontend code formatting (`src/frontend/.prettierrc.json`)
- vite-plugin-checker 0.11.0 - Type/lint checking in Vite

## Key Dependencies

**Critical:**
- `io.jsonwebtoken:jjwt-api` 0.13.0 - JWT token creation and verification
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` - Schema migration management
- `org.postgresql:postgresql` - PostgreSQL JDBC driver (runtime)
- `com.zaxxer:HikariCP` (via Spring Boot) - Connection pooling
- `org.hibernate.orm:hibernate-envers` 6.6.14 - Audit logging for entity changes
- `io.hypersistence:hypersistence-utils-hibernate-63` 3.9.10 - Advanced Hibernate/PostgreSQL types
- `com.vladmihalcea:hibernate-types-60` 2.21.1 - Hibernate JSON/array type support
- `org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j` - Circuit breaker for external calls
- `org.springframework.retry:spring-retry` - Retry logic for email and external services

**Infrastructure:**
- `io.micrometer:micrometer-registry-prometheus` (runtime) - Prometheus metrics export via Actuator
- `net.logstash.logback:logstash-logback-encoder` 8.1 - JSON structured logging
- `com.bucket4j:bucket4j-core` 8.10.1 - In-memory rate limiting
- `org.mapstruct:mapstruct` 1.6.3 - DTO-entity mapping with annotation processing
- `org.projectlombok:lombok` - Boilerplate reduction (compile-time only)
- `org.jasypt:jasypt` 1.9.3 - Password/field encryption utilities
- `com.googlecode.libphonenumber:libphonenumber` 9.0.25 - Phone number parsing/validation
- `org.sqids:sqids` 0.1.0 - Obfuscated ID encoding (for short codes / public IDs)
- `commons-codec:commons-codec` 1.19.0 - Encoding utilities
- `com.github.ua-parser:uap-java` 1.6.1 - User-agent string parsing
- `com.google.guava:guava` 33.4.8-jre - General utilities
- `org.apache.commons:commons-lang3` 3.20.0 - String/object utilities
- `org.apache.commons:commons-text` 1.13.1 - Text utilities
- `commons-validator:commons-validator` 1.9.0 - Email and general validation
- `net.ttddyy:datasource-proxy` 1.10 - DataSource interception for query logging
- `pinia` 3.0.1 - Frontend state management
- `vue-router` 4.0.0 - Frontend routing
- `vue-i18n` 11.0.0 - Internationalization (en-US and fr-FR)
- `axios` 1.2.1 - HTTP client for frontend API calls
- `@rajesh896/broprint.js` 2.2.0 - Browser fingerprinting for fraud prevention

## Configuration

**Environment:**
- Application config: `src/main/resources/application.yaml` (production defaults)
- Dev override: `src/main/resources/application-dev.yaml` (activated via Spring profile)
- Secrets injected via environment variables: `SPRING_MAIL_PASSWORD`, `GMX_PASSWORD`, `GMAIL_PASSWORD`, `MAIL_DE_PASSWORD`, `MOMO_SUBSCRIPTION_KEY`, `LOKI_API_KEY`
- Allowed client IDs: `allowed.clients` property (comma-separated list)

**Build:**
- `pom.xml` - Maven build orchestration
- `src/frontend/package-lock.json` - npm lockfile
- Frontend is built by Maven and output copied to `target/classes/static` for Spring Boot to serve as static resources

## Platform Requirements

**Development:**
- Java 17+ JDK
- Maven (or use `./mvnw`)
- Node.js v22+ (auto-installed by frontend-maven-plugin during build)
- PostgreSQL database (or Docker for Testcontainers-based tests)

**Production:**
- JVM 17+ runtime
- PostgreSQL database at `jdbc:postgresql://localhost/sendam?TimeZone=UTC` (configurable)
- Log directory: `/var/log/sendam/`
- Access log directory: `/usr/local/var/ledger/`
- Port: 9990 (default, configurable via `${port}`)

---

*Stack analysis: 2026-03-09*
