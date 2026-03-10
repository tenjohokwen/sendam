# Codebase Structure

**Analysis Date:** 2026-03-09

## Directory Layout

```
sendam/                                    # Project root
├── pom.xml                                # Maven build — single-module; orchestrates Java + frontend build
├── mvnw / mvnw.cmd                        # Maven wrapper scripts
├── ARCHITECTURE.md                        # Security module architecture doc (pre-existing)
├── docs/                                  # Supplementary documentation
├── src/
│   ├── frontend/                          # Vue 3 / Quasar SPA (built by Maven frontend-maven-plugin)
│   │   ├── src/
│   │   │   ├── App.vue                    # Vue application root
│   │   │   ├── api/                       # Axios API call modules (one file per domain)
│   │   │   │   ├── auth.api.js
│   │   │   │   ├── account.api.js
│   │   │   │   ├── profile.api.js
│   │   │   │   ├── session.api.js
│   │   │   │   └── index.js               # Re-exports all api modules
│   │   │   ├── boot/                      # Quasar boot files (run before app mounts)
│   │   │   │   ├── axios.js               # Axios instance, interceptors, fingerprint cookie
│   │   │   │   └── i18n.js                # i18n initialization
│   │   │   ├── composables/               # Vue 3 composables (reusable reactive logic)
│   │   │   │   ├── useSession.js
│   │   │   │   ├── useErrorHandler.js
│   │   │   │   └── useLoading.js
│   │   │   ├── components/                # Reusable Vue components
│   │   │   │   ├── common/               # Shared/generic components
│   │   │   │   └── profile/              # Profile-specific components
│   │   │   ├── layouts/
│   │   │   │   └── MainLayout.vue         # App shell with session monitoring
│   │   │   ├── pages/                     # Route-level page components
│   │   │   │   ├── IndexPage.vue
│   │   │   │   ├── DashboardPage.vue
│   │   │   │   ├── ProfilePage.vue
│   │   │   │   ├── ErrorNotFound.vue
│   │   │   │   └── auth/                  # Auth pages
│   │   │   │       ├── LoginPage.vue
│   │   │   │       ├── RegisterPage.vue
│   │   │   │       ├── ActivatePage.vue
│   │   │   │       ├── OtpPage.vue
│   │   │   │       ├── ForgotPasswordPage.vue
│   │   │   │       └── ResetPasswordPage.vue
│   │   │   ├── plugins/                   # Non-Quasar plugins (e.g., sessionManager)
│   │   │   ├── router/                    # Vue Router — routes + navigation guards
│   │   │   ├── stores/                    # Pinia stores
│   │   │   │   ├── index.js               # Pinia instance
│   │   │   │   └── example-store.js
│   │   │   ├── utils/                     # Pure utility functions
│   │   │   ├── css/                       # Global CSS / SCSS
│   │   │   ├── assets/                    # Static images, fonts
│   │   │   └── i18n/                      # Translation files
│   │   │       ├── en-US/
│   │   │       └── fr-FR/
│   │   ├── dist/spa/                      # Built SPA output (committed, copied to Spring static)
│   │   ├── public/                        # Quasar public assets (icons, etc.)
│   │   └── package.json                   # Frontend dependencies
│   │
│   ├── main/
│   │   ├── java/com/softropic/sendam/
│   │   │   ├── AppTemplateApplication.java    # Spring Boot entry point (@SpringBootApplication)
│   │   │   ├── common/                        # Global cross-module utilities
│   │   │   │   ├── persistence/               # Base entities, auditing, DB utilities
│   │   │   │   │   ├── AbstractAuditingEntity.java
│   │   │   │   │   └── BaseEntity.java
│   │   │   │   ├── message/                   # API response types (Response, Success, Failure, ErrorDto)
│   │   │   │   ├── exception/                 # Global exceptions (ApplicationException, ResourceNotFoundException)
│   │   │   │   ├── validation/                # Custom bean validation annotations + validators
│   │   │   │   ├── client/                    # HTTP client base classes (AbstractClient, RestRequestInterceptor)
│   │   │   │   ├── payment/                   # Payment provider enums/interfaces
│   │   │   │   ├── refund/                    # Refund policy types
│   │   │   │   ├── threadpool/                # MDC-aware thread pool wrappers
│   │   │   │   ├── logging/                   # Log key constants
│   │   │   │   ├── dto/                       # Global shared DTOs (PhoneNumberDto)
│   │   │   │   ├── enums/                     # Global enums (Picker, Unit)
│   │   │   │   ├── config/                    # Common Spring configuration (CommonConfig)
│   │   │   │   └── consumer/                  # Consumer domain types
│   │   │   │
│   │   │   ├── config/                        # Root-level Spring configuration
│   │   │   │   └── DataSourceConfig.java      # DataSource / Flyway wiring
│   │   │   │
│   │   │   ├── security/                      # Security vertical slice
│   │   │   │   ├── api/                       # REST controllers, facades, registration strategies
│   │   │   │   │   ├── AccountResource.java
│   │   │   │   │   ├── ProfileResource.java
│   │   │   │   │   ├── AccountManagementFacade.java
│   │   │   │   │   ├── ApiAdvice.java         # @RestControllerAdvice — global error handler
│   │   │   │   │   ├── dto/                   # API-only DTOs (never passed to service)
│   │   │   │   │   ├── ratelimit/             # Rate limit configuration helpers
│   │   │   │   │   └── registration/          # Registration strategy implementations
│   │   │   │   ├── contract/                  # Shared passive types (no Spring beans)
│   │   │   │   │   ├── event/                 # Cross-layer Spring events
│   │   │   │   │   ├── exception/             # All security exceptions
│   │   │   │   │   └── util/                  # Stateless utilities, constants, annotations
│   │   │   │   ├── service/                   # Domain business logic
│   │   │   │   ├── infrastructure/            # Technical implementations
│   │   │   │   │   ├── jwt/                   # JWT token management
│   │   │   │   │   │   └── filter/            # JWTAuthenticationFilter, JWTAuthorizationFilter
│   │   │   │   │   ├── filter/                # Security filters (2FA, session refresh, advice)
│   │   │   │   │   ├── listener/              # Spring event listeners
│   │   │   │   │   └── audit/                 # JPA auditing (SpringSecurityAuditorAware)
│   │   │   │   ├── repo/                      # JPA entities + Spring Data repositories
│   │   │   │   ├── common/                    # Internal security cross-cutting utilities
│   │   │   │   │   ├── event/                 # Internal security events (AuthEvent, FraudEvent)
│   │   │   │   │   └── util/                  # CookieUtil, SecurityConstants, RequestMetadataProvider
│   │   │   │   ├── config/                    # Security Spring wiring (SecurityConfiguration)
│   │   │   │   └── audit/                     # Security audit trail sub-module (pending migration)
│   │   │   │       ├── api/                   # Audit REST endpoints
│   │   │   │       ├── filter/                # Logging filter
│   │   │   │       ├── listener/              # Audit event listeners
│   │   │   │       ├── repository/            # AuditLog JPA repository
│   │   │   │       ├── service/               # TrailService
│   │   │   │       └── shared/event/
│   │   │   │
│   │   │   └── email/                         # Email vertical slice
│   │   │       ├── contract/                  # EmailTemplate, Envelope, Recipient, EmailProperties
│   │   │       ├── service/                   # MailService, MailManager, SenderProvider, ResendEmailService
│   │   │       ├── infrastructure/            # EmailRetryScheduler, MailSenderProvider
│   │   │       │   └── listener/              # AccountChangeEmailListener
│   │   │       ├── repo/                      # EnvelopeEntity, RecipientEntity, EnvelopeEntityRepository
│   │   │       └── config/                    # Email Spring wiring (AsyncConfig, ThymeleafConfiguration)
│   │   │
│   │   └── resources/
│   │       ├── application.yaml               # Main configuration (all environments base)
│   │       ├── application-dev.yaml           # Dev profile overrides
│   │       ├── db/migration/                  # Flyway SQL migration scripts
│   │       ├── mails/                         # Thymeleaf email templates (.html)
│   │       ├── i18n/                          # Backend message bundles (messages.properties)
│   │       ├── static/                        # Spring Boot static asset serving (empty; SPA copied here at build)
│   │       └── config/
│   │           ├── logback-spring.xml
│   │           ├── blacklisted-names.json
│   │           └── whitelisted-emails.json
│   │
│   └── test/
│       ├── java/com/softropic/sendam/         # Mirrors main package structure
│       │   ├── common/                        # Common utility tests
│       │   ├── security/                      # Security module tests
│       │   ├── email/                         # Email module tests
│       │   ├── config/                        # Test-specific Spring configs
│       │   └── utils/sql/matcher/             # SQL assertion helpers
│       └── resources/sql/                     # SQL data fixtures for integration tests
│
└── target/                                    # Maven build output (generated, not committed)
```

## Directory Purposes

**`src/main/java/com/softropic/sendam/common/`:**
- Purpose: Global utilities shared across vertical slices
- Contains: Base entity classes, standard API response types, custom validation annotations, HTTP client base, MDC thread wrappers
- Key files: `persistence/AbstractAuditingEntity.java`, `message/Success.java`, `message/ErrorDto.java`, `validation/PhoneNumberValidator.java`

**`src/main/java/com/softropic/sendam/security/`:**
- Purpose: Authentication, authorization, user management, session, 2FA, rate limiting
- Contains: Full `api/contract/service/infrastructure/repo/common/config/audit` layer stack
- Key files: `api/AccountResource.java`, `api/AccountManagementFacade.java`, `api/ApiAdvice.java`, `repo/User.java`, `service/UserService.java`, `infrastructure/jwt/filter/JWTAuthenticationFilter.java`

**`src/main/java/com/softropic/sendam/email/`:**
- Purpose: Transactional email delivery with retry and multi-provider support
- Contains: `contract/service/infrastructure/repo/config` layers; Thymeleaf rendering, SMTP sending, DB-backed retry queue
- Key files: `service/MailService.java`, `service/MailManager.java`, `infrastructure/EmailRetryScheduler.java`, `repo/EnvelopeEntity.java`

**`src/main/resources/db/migration/`:**
- Purpose: Flyway SQL migration scripts — source of truth for DB schema
- Contains: Versioned `.sql` files (`V1__init.sql`, etc.)
- Generated: No — hand-authored
- Committed: Yes

**`src/main/resources/mails/`:**
- Purpose: Thymeleaf HTML email templates, one per `EmailTemplate` enum value
- Contains: `.html` template files named in lower-camel form of `EmailTemplate` enum constants (e.g., `passwordReset.html`)

**`src/frontend/src/api/`:**
- Purpose: All backend API calls — one file per domain area
- Contains: Plain JS objects with methods wrapping the shared `api` Axios instance
- Key files: `auth.api.js`, `account.api.js`, `profile.api.js`, `session.api.js`

**`src/frontend/src/boot/`:**
- Purpose: Quasar boot files — run once before the Vue app mounts
- Contains: Axios instance configuration, i18n initialization
- Key files: `axios.js` (interceptors, fingerprint cookie, loading state pub/sub), `i18n.js`

**`src/frontend/src/composables/`:**
- Purpose: Vue 3 composables — reusable reactive logic extracted from components
- Contains: `useSession.js`, `useErrorHandler.js`, `useLoading.js`

**`src/frontend/src/plugins/`:**
- Purpose: Non-Quasar plugin modules (pure JS)
- Contains: `sessionManager` — session expiry monitoring, activity tracking, refresh logic

**`src/frontend/dist/spa/`:**
- Purpose: Compiled Quasar SPA output; Maven copies this to `target/classes/static` at build time
- Generated: Yes (by `npx quasar build`)
- Committed: Yes (so Spring Boot serves static assets without requiring a separate frontend build step in dev)

## Key File Locations

**Entry Points:**
- `src/main/java/com/softropic/sendam/AppTemplateApplication.java`: Spring Boot application entry
- `src/frontend/src/App.vue`: Vue application root

**Configuration:**
- `src/main/resources/application.yaml`: Base Spring configuration (server port, DB, mail, CORS, actuator)
- `src/main/resources/application-dev.yaml`: Development profile overrides
- `src/main/java/com/softropic/sendam/security/config/SecurityConfiguration.java`: Spring Security filter chain, CORS, session, authorization rules
- `src/main/java/com/softropic/sendam/config/DataSourceConfig.java`: DataSource / HikariCP / Flyway wiring
- `pom.xml`: Maven dependencies, frontend build integration, compiler annotation processors

**Core Logic:**
- `src/main/java/com/softropic/sendam/security/api/AccountManagementFacade.java`: User registration, password reset, email change orchestration
- `src/main/java/com/softropic/sendam/security/service/UserService.java`: User domain logic
- `src/main/java/com/softropic/sendam/security/infrastructure/jwt/filter/JWTAuthenticationFilter.java`: Login endpoint handler
- `src/main/java/com/softropic/sendam/security/infrastructure/jwt/filter/JWTAuthorizationFilter.java`: Per-request JWT validation
- `src/main/java/com/softropic/sendam/email/service/MailManager.java`: Email persistence and dispatch coordination
- `src/main/java/com/softropic/sendam/common/persistence/AbstractAuditingEntity.java`: Base entity for all JPA entities

**API Response Types:**
- `src/main/java/com/softropic/sendam/common/message/Success.java`: Standard success response record
- `src/main/java/com/softropic/sendam/common/message/ErrorDto.java`: Standard error response with field-level detail
- `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`: Centralized exception-to-response translation

**Testing:**
- `src/test/java/com/softropic/sendam/`: Mirrors production package structure
- `src/test/resources/sql/`: SQL fixture files loaded in integration tests
- `src/test/java/com/softropic/sendam/utils/sql/matcher/`: Custom SQL assertion matchers

## Naming Conventions

**Java Files:**
- Controllers: `{Domain}Resource.java` (e.g., `AccountResource`, `ProfileResource`)
- Facades: `{Domain}Facade.java` (e.g., `AccountManagementFacade`)
- Services: `{Domain}Service.java` (e.g., `UserService`, `MailService`)
- Interfaces in `service/`: noun or verb phrase (e.g., `LoginTokenManager`, `LoginDecisionManager`, `LoginAttemptConsumer`)
- Infrastructure implementations: `{Interface}Impl.java` (e.g., `JwtManagerImpl`, `TokenCreatorImpl`)
- Filters: `{Name}Filter.java`
- Listeners: `{Trigger}Listener.java` (e.g., `AuthenticationSuccessListener`, `AccountChangeEmailListener`)
- JPA Entities: plain noun, no suffix (e.g., `User`, `LoginInfo`, `SecKey`, `EnvelopeEntity`)
- Repositories: `{Entity}Repository.java`
- DTOs: `{Name}Dto.java`
- Events: `{Name}Event.java`
- Exceptions: `{Name}Exception.java` or `{Name}Error.java` (errors are enums/constants)
- Config classes: `{Domain}Config.java` or `{Domain}Configuration.java`

**Frontend Files:**
- API modules: `{domain}.api.js` (e.g., `auth.api.js`)
- Composables: `use{Name}.js` (e.g., `useSession.js`, `useLoading.js`)
- Pages: `{Name}Page.vue` (e.g., `LoginPage.vue`, `DashboardPage.vue`)
- Layouts: `{Name}Layout.vue`

**Directories:**
- Backend: lowercase package names following Java convention
- Frontend: lowercase kebab-case (e.g., `src/api/`, `src/boot/`, `src/composables/`)

## Where to Add New Code

**New REST endpoint in an existing domain (e.g., security):**
- Controller method: `src/main/java/com/softropic/sendam/security/api/AccountResource.java` or a new `{Domain}Resource.java`
- If orchestration spans multiple services: add to `AccountManagementFacade.java` or create `{Domain}Facade.java`
- Apply `@RateLimited` for any endpoint accepting user input
- Tests: `src/test/java/com/softropic/sendam/security/api/`

**New domain service:**
- Interface (if implementation is technical): `src/main/java/com/softropic/sendam/security/service/{Name}.java`
- Implementation: `src/main/java/com/softropic/sendam/security/infrastructure/{Name}Impl.java`
- Pure domain logic: `src/main/java/com/softropic/sendam/security/service/{Name}Service.java`

**New JPA entity:**
- Entity class: `src/main/java/com/softropic/sendam/security/repo/{Name}.java` — extend `AbstractAuditingEntity`
- Repository interface: `src/main/java/com/softropic/sendam/security/repo/{Name}Repository.java`
- Migration: `src/main/resources/db/migration/V{N}__{description}.sql`

**New DTO crossing layers:**
- Location: `src/main/java/com/softropic/sendam/security/contract/{Name}Dto.java`
- Rule: If it never leaves the `api` layer, use `src/main/java/com/softropic/sendam/security/api/dto/{Name}Dto.java`

**New exception:**
- Location: `src/main/java/com/softropic/sendam/security/contract/exception/{Name}Exception.java`
- Add handler to: `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`

**New Spring application event:**
- If consumed across layers: `src/main/java/com/softropic/sendam/security/contract/event/{Name}Event.java`
- If internal to security filter/listener wiring only: `src/main/java/com/softropic/sendam/security/common/event/{Name}Event.java`

**New email template:**
- Enum entry: `src/main/java/com/softropic/sendam/email/contract/EmailTemplate.java`
- Template file: `src/main/resources/mails/{enumName in lowerCamel}.html`
- Subject key: `src/main/resources/i18n/messages.properties`

**New frontend page:**
- Page component: `src/frontend/src/pages/{Name}Page.vue`
- Route: `src/frontend/src/router/routes.js` — add `meta.requiresAuth: true` or `meta.requiresGuest: true`

**New frontend API call:**
- Add method to existing: `src/frontend/src/api/{domain}.api.js`
- Or create: `src/frontend/src/api/{newdomain}.api.js` and re-export from `src/frontend/src/api/index.js`

**Shared global utilities (used across multiple vertical slices):**
- Location: `src/main/java/com/softropic/sendam/common/util/{Name}Util.java`

## Special Directories

**`src/frontend/node/`:**
- Purpose: Node.js runtime installed by `frontend-maven-plugin` during build
- Generated: Yes (by Maven)
- Committed: No (in practice — large binary; should be gitignored)

**`src/frontend/lib/`:**
- Purpose: Global npm packages installed by frontend-maven-plugin (e.g., `@quasar/cli`)
- Generated: Yes
- Committed: No

**`src/frontend/dist/`:**
- Purpose: Quasar build output
- Generated: Yes (`npx quasar build`)
- Committed: Yes (dist/spa is committed so the app runs without running frontend build separately)

**`target/`:**
- Purpose: Maven build output (compiled classes, JAR, test reports)
- Generated: Yes
- Committed: No

**`.planning/`:**
- Purpose: GSD planning documents
- Generated: Yes (by GSD tooling)
- Committed: Yes

---

*Structure analysis: 2026-03-09*
