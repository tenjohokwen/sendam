# Coding Conventions

**Analysis Date:** 2026-03-09

## Naming Patterns

**Classes:**
- PascalCase for all class names
- Suffix `Service` for Spring service beans: `UserService`, `LoginAttemptsService`, `PasswordResetService`
- Suffix `Repository` for Spring Data repositories: `UserRepository`, `SecKeyRepository`
- Suffix `Facade` for orchestration layer classes: `AccountManagementFacade`
- Suffix `IT` for integration tests: `UserServiceIT`, `SecurityIT`, `EmailRetrySchedulerIT`
- Suffix `Test` for unit tests: `JwtManagerImplTest`, `RateLimitingServiceTest`
- Suffix `Dto` for data transfer objects: `UserDto`, `ChangePasswordDto`, `ErrorDto`
- Suffix `Entity` for JPA persistence-tier models that are distinct from domain models: `EnvelopeEntity`
- Suffix `Mapper` for MapStruct mapper interfaces: `UserMapper`
- Suffix `Config` for configuration classes: `TestConfig`, `JwtConfiguration`
- Suffix `Exception` for unchecked exceptions: `ApplicationException`, `SecException`
- Suffix `Provider` for infrastructure/strategy providers: `ClockProvider`, `RequestMetadataProvider`
- Suffix `Listener` for Spring event listeners: `AuthenticationFailureListener`, `QueryRecorderListener`
- Suffix `Aspect` for AOP aspects: `RateLimitingAspect`
- Suffix `Filter` for servlet filters: `JWTAuthenticationFilter`, `SecurityAdviceFilter`
- Suffix `Handler` for handler classes: `ApiAdvice` (exception handler), `AjaxLogoutSuccessHandler`

**Methods:**
- camelCase for all methods
- Boolean predicates use `is` prefix: `isActivated()`, `isLocked()`, `isAllowed()`, `hasAccountExpired()`
- `can` prefix for permission-check predicates: `canInitiatePasswordReset()`
- `has` prefix for state-check predicates: `hasValidResetKey()`, `hasAccountExpired()`
- `find` prefix for nullable/optional returns: `findUserByLogin()`, `findUserById()`
- `get` prefix for non-null required returns: `getUserWithAuthorities()`
- `create` for factory-like operations within tests: `createPrincipal()`, `createAdminPrincipal()`

**Variables:**
- camelCase for all local variables and fields
- `final` used extensively on local variables: `final User fetchedUser = ...`
- Constants use SCREAMING_SNAKE_CASE: `SEC_DATA_SQL_PATH`, `LOGIN_NAME`, `MAX_FAILED_CLIENT_ATTEMPTS`

**Types:**
- Domain entities in `repo` package (without `Entity` suffix): `User`, `Authority`, `Secret`, `LoginInfo`
- Persistence-tier models outside domain use `Entity` suffix: `EnvelopeEntity`
- Java records used for value objects: `Success(String helpCode, String msgKey, String msg, Map<String, Object> payload)`
- Interfaces used for abstract contracts: `Response`, `ErrorCode`, `Client`
- Enums for closed value sets: `EntityStatus`, `LoginIdType`, `Gender`, `EmailDeliveryStatus`

**Packages:**
- `common` for cross-cutting shared code
- `security` for authentication/authorization domain
- `email` for email domain
- Sub-packages by stereotype: `.repo`, `.service`, `.api`, `.infrastructure`, `.contract`, `.config`

## Code Style

**Formatting:**
- Java: No linting config file; standard IntelliJ Java style implied by codebase
- Frontend (Vue/JS): Prettier configured in `src/frontend/.prettierrc.json`
  - No semicolons (`"semi": false`)
  - Single quotes (`"singleQuote": true`)
  - Print width 100 characters (`"printWidth": 100`)

**Lombok Usage:**
- `@Slf4j` on service classes for SLF4J logger injection
- `@RequiredArgsConstructor` on services for constructor injection
- `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor` on entity base classes
- Do NOT use Lombok on classes where manual constructor logic is required (e.g., `AccountManagementFacade` has a manual constructor for `@Value` injection)

**Spring Annotations:**
- `@Service` on all service beans
- `@Transactional` at class level on services, with `@Transactional(readOnly = true)` overriding individual read methods
- `@PreAuthorize` for method-level security using constants from `SecurityConstants`
- `@RestControllerAdvice` on the single global exception handler `ApiAdvice`
- `@TestConfiguration(proxyBeanMethods = false)` on test config classes

## Import Organization

**Order (observed pattern):**
1. Application classes (`com.softropic.sendam.*`)
2. Third-party library classes (Jackson, JWT, Lombok, etc.)
3. Spring Framework classes (`org.springframework.*`)
4. Java standard library (`java.*`, `javax.*`, `jakarta.*`)
5. Static imports at the end

**Path Aliases:**
- No path aliases in Java backend
- Frontend uses `src/` as root

## Error Handling

**Pattern: Custom Exception Hierarchy**
- All domain exceptions extend `ApplicationException` (which extends `RuntimeException`)
- `ApplicationException` auto-generates a `supportId` (Sqids-encoded UUID) for tracing
- `ApplicationException` carries a `logContext: Map<String, Object>` for structured logging
- `ApplicationException` carries an `ErrorCode` enum value for machine-readable codes
- Security-specific exceptions extend `SecException` extends `ApplicationException`
- Example hierarchy: `UserNotFoundException` → `SecException` → `ApplicationException`

**Pattern: Global Exception Handler**
- Single `@RestControllerAdvice` class: `src/main/java/com/softropic/sendam/security/api/ApiAdvice.java`
- Every `@ExceptionHandler` returns `ErrorDto` with a `helpCode` (support ID visible to the user)
- Security exceptions publish `SecurityAlertEvent` in addition to returning an error response
- All responses logged with structured arguments via logstash: `log.error(fullMsg, entries(ctx), throwable)`

**Pattern: Optional for nullable returns**
- Service methods return `Optional<T>` for queries that may find nothing
- `orElseThrow(() -> new UserNotFoundException(...))` used to convert `Optional` to exceptions on required lookups

**Pattern: Business methods on entities**
- Rich domain model: business logic on entities, not just setters (e.g., `User.activate()`, `User.lock()`, `User.preparePasswordReset()`)
- Business rule violations throw domain exceptions from inside entity methods

## Logging

**Framework:** SLF4J with Logback + logstash-logback-encoder (`net.logstash.logback`)

**Patterns:**
- `@Slf4j` (Lombok) injects `log` field on annotated classes
- Structured logging via `entries(ctx)` from `net.logstash.logback.argument.StructuredArguments`
- Log context keys defined as constants in `src/main/java/com/softropic/sendam/common/logging/LogKeys.java`
- Manual `Logger` via `LoggerFactory.getLogger(...)` used where Lombok is not present (e.g., `AccountManagementFacade`)
- Error logs always include a `SUPPORT_ID` for correlation with user-facing `helpCode`

## Comments

**When to Comment:**
- Public method Javadoc used on complex methods and exception handlers
- Inline comments for non-obvious business rules (especially security logic)
- TODO comments for known gaps and deferred work (seen in `InputValidator`, `User`, `EntityFetchAsserter`)

**Javadoc:**
- Used on `@ExceptionHandler` methods in `ApiAdvice` to explain which exception type is handled and why
- Used on public API methods in domain entities (e.g., `User.activate()`, `User.preparePasswordReset()`)
- `@throws` tags included in entity Javadoc

## Function Design

**Size:** Methods are kept focused; services delegate to specific sub-services (e.g., `AccountManagementFacade` orchestrates `UserRegistrationService`, `UserProfileService`, `PasswordResetService`)

**Parameters:** Constructor injection preferred over field injection; `@Value` used for configuration values injected into constructors

**Return Values:**
- `Optional<T>` for queries that may return nothing
- Domain objects returned directly from service methods
- `Success`/`ErrorDto` records returned from REST layer
- `void` for write operations that are side-effect only

## Module Design

**Exports:** Package-private visibility used for internal service classes; public for API-facing facades and contracts

**Barrel Files:** Not applicable (Java); each class in its own file

**Dependency Direction:**
- `api` layer → `service` layer → `repo` layer
- `contract` package is a dependency-free contracts module (DTOs, interfaces, exceptions)
- `infrastructure` package contains technical implementations (JWT, filters, aspects)
- `common` package is a shared utility module used by all layers

---

*Convention analysis: 2026-03-09*
