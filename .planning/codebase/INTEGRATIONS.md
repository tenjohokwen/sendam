# External Integrations

**Analysis Date:** 2026-03-09

## APIs & External Services

**Mobile Money Payments:**
- MTN MoMo (Mobile Money) - Payment collection for Cameroon market
  - SDK/Client: Custom `AbstractClient` / `RestTemplate` (`src/main/java/com/softropic/sendam/common/client/AbstractClient.java`)
  - Auth: `MOMO_SUBSCRIPTION_KEY` env var (sent as `Ocp-Apim-Subscription-Key` header)
  - Base host: `https://sandbox.momodeveloper.mtn.com/collection` (sandbox environment)
  - Endpoints configured in `application.yaml` under `client.momo.endpoints`:
    - `POST /v1_0/requesttopay` - Initiate payment
    - `GET /v1_0/requesttopay/{referenceId}` - Check transaction status
    - `GET /v1_0/account/balance` - Get account balance
    - `GET /v1_0/accountholder/{type}/{id}/basicuserinfo` - Get account holder info
    - `POST /collection/token/` - Request auth token
  - Supported providers enum: `MTN`, `ORANGE`, `NEXTTEL` (`src/main/java/com/softropic/sendam/common/payment/MobilePaymentProvider.java`)
  - Error type: `MomoError` (`src/main/java/com/softropic/sendam/common/client/exception/MomoError.java`)

**Observability:**
- Grafana Loki - Centralized log aggregation
  - Config: `src/main/resources/config/logback-spring.xml`
  - Push URL: `https://logs-prod-012.grafana.net/loki/api/v1/push`
  - Auth: Basic auth with username `1350490` and `LOKI_API_KEY` env var
  - Log format: JSON structured logs with `@timestamp`, `level`, `class`, `thread`, `message`, `traceId`, `spanId`
  - Library: `loki4j` Logback appender (referenced in `logback-spring.xml` as `com.github.loki4j.logback.Loki4jAppender`)

## Data Storage

**Databases:**
- PostgreSQL
  - Connection: `JDBC_DATABASE_URL` or configured via `spring.datasource.url` in `application.yaml`
  - Default URL: `jdbc:postgresql://localhost/sendam?TimeZone=UTC`
  - Default credentials: `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` (defaults `postgres`/`postgres` in dev)
  - Client: Spring Data JPA + Hibernate 6 (`spring-boot-starter-data-jpa`)
  - Connection pool: HikariCP, max 25 connections, pool name `hikari-db-pool`
  - Schema: `main` (Hibernate `default_schema`)
  - Migrations: Flyway (`src/main/resources/db/migration/`)
  - Audit: Hibernate Envers enabled (`org.hibernate.orm:hibernate-envers`), configured to store data at delete
  - DataSource proxy: `net.ttddyy:datasource-proxy` for SQL query logging (conditional on `log.database.spy`)
  - Testcontainers PostgreSQL: used in integration tests (`src/test/java/com/softropic/sendam/config/CustomPostgresContainer.java`)

**File Storage:**
- Local filesystem only (log files at `/var/log/sendam/`, access logs at `/usr/local/var/ledger/`)

**Caching:**
- Spring Cache abstraction enabled (`spring-boot-starter-cache`); specific cache provider not detected in config (defaults to in-memory ConcurrentMap)

## Authentication & Identity

**Auth Provider:**
- Custom (no external OAuth / SSO provider)
  - Implementation: Custom Spring Security filter chain (`src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java`)
  - Login: username/email/phone via `POST /authenticate`
  - Passwords: BCrypt encoded (`BCryptPasswordEncoder`, 10 rounds)
  - Session token: JWT stored in HTTP-only cookies (`JWT_COOKIE_NAME`)
  - Two-Factor Authentication (2FA): OTP flow via `POST /otp`, managed by `TwoFactorLoginService`
  - Fraud detection: `FraudAwareAuthenticationManager` + browser fingerprint cookie (`fcookie`) from `@rajesh896/broprint.js`
  - Client-ID enforcement: `ClientIdAccessDecisionManager` validates `allowed.clients` list
  - Encryption: `org.jasypt:jasypt` for field-level encryption; `Cryptopher` utility class (`src/main/java/com/softropic/sendam/security/contract/util/Cryptopher.java`)
  - Short codes: `org.sqids:sqids` for obfuscated public IDs (`ShortCode` utility)

## Monitoring & Observability

**Metrics:**
- Prometheus metrics via Micrometer (`micrometer-registry-prometheus`) exposed at `/manage/actuator`
- Actuator endpoints: `/manage/*` (all endpoints in default config; restricted in dev to `health`, `info`, `env`)
- Health endpoint: requires `ROLE_ADMIN` to view details

**Logs:**
- Logback with three appenders (all active):
  1. Console - INFO level minimum
  2. Rolling file - `/var/log/sendam/spring.log` (10MB max, 30 days history, 1GB total cap)
  3. Grafana Loki - JSON structured push (see above)
- Log format: JSON via `logstash-logback-encoder` 8.1
- Tomcat access log: custom pattern at `/usr/local/var/ledger/sendam_access*.ledger` (includes request body length, session tracker)

**Resilience:**
- Resilience4j circuit breaker via `spring-cloud-starter-circuitbreaker-resilience4j`
- Spring Retry (`@EnableRetry` on `AppTemplateApplication`) - used in email retry scheduler (`src/main/java/com/softropic/sendam/email/infrastructure/EmailRetryScheduler.java`)
- TCP config defaults: 15s read/connection timeout, 2s connection-request timeout, 50 max connections per client

## CI/CD & Deployment

**Hosting:**
- Not detected in codebase (no Dockerfile, `render.yaml`, `fly.toml`, etc. found)

**CI Pipeline:**
- Not detected (no `.github/workflows`, `.gitlab-ci.yml`, `Jenkinsfile`, etc. found)

## Email / SMTP

**Providers Configured:**
- GMX (`mail.gmx.net:587`) - Auth: `GMX_PASSWORD` env var; username `blue-bone@gmx.de`
- Gmail (`smtp.gmail.com:587`) - Auth: `GMAIL_PASSWORD` env var; username `enkap24@gmail.com`
- Mail.de (`smtp.mail.de:587`) - Auth: `MAIL_DE_PASSWORD` env var; username `blue-bone@mail.de`
- Spring Mail default (also GMX) - Auth: `SPRING_MAIL_PASSWORD` env var
- Multiple providers managed under `email.providerConfigs` with retry/fallback logic
- Email listener: `src/main/java/com/softropic/sendam/email/infrastructure/listener/`
- Email retry scheduler: `src/main/java/com/softropic/sendam/email/infrastructure/EmailRetryScheduler.java`
- Templates: Thymeleaf templates in `src/main/resources/mails/`
- i18n: English (`messages_en.properties`) and French (`messages_fr.properties`) message bundles in `src/main/resources/i18n/`

## Environment Configuration

**Required env vars:**
- `SPRING_MAIL_PASSWORD` - Primary SMTP password (GMX)
- `GMX_PASSWORD` - GMX SMTP password
- `GMAIL_PASSWORD` - Gmail SMTP password
- `MAIL_DE_PASSWORD` - Mail.de SMTP password
- `MOMO_SUBSCRIPTION_KEY` - MTN MoMo API subscription key
- `LOKI_API_KEY` - Grafana Loki push API key

**Secrets location:**
- Environment variables only; no secrets manager detected

## Webhooks & Callbacks

**Incoming:**
- None detected (no dedicated webhook endpoint found in Java sources)

**Outgoing:**
- MTN MoMo requestToPay callback: not explicitly configured in application config; standard MoMo pattern requires a callback URL but none is defined in the current `application.yaml`

---

*Integration audit: 2026-03-09*
