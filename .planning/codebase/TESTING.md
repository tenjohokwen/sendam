# Testing Patterns

**Analysis Date:** 2026-03-09

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) — managed via `spring-boot-starter-test`
- Maven Failsafe Plugin runs integration tests (`*IT.java`) in the `verify` phase
- Maven Surefire Plugin runs unit tests (`*Test.java`) in the `test` phase

**Assertion Library:**
- AssertJ (`assertj-core` 3.24.2) — primary assertion library
- `json-unit-assertj` 4.1.0 for JSON-specific assertions
- Awaitility 4.2.0 for async test assertions

**Mocking:**
- Mockito (`mockito-core`) for unit test mocking
- `MockitoAnnotations.openMocks(this)` used in `@BeforeEach` (not `@ExtendWith(MockitoExtension.class)`)
- `MockedStatic` used for mocking static methods (e.g., `RequestMetadataProvider`)

**Test Data Generation:**
- Instancio (`instancio-core` 2.10.0) for random object generation: `Instancio.create(Address.class)`
- SQL fixture files for database seed data

**Run Commands:**
```bash
./mvnw test                    # Run unit tests only
./mvnw verify                  # Run unit + integration tests
./mvnw failsafe:integration-test  # Run integration tests only
```

## Test File Organization

**Location:**
- All test files under `src/test/java/com/softropic/sendam/`
- Mirrors the main source package structure

**Naming:**
- Unit tests: `{ClassName}Test.java` (e.g., `LoginAttemptsServiceTest.java`, `RateLimitingServiceTest.java`)
- Integration tests: `{ClassName}IT.java` (e.g., `UserServiceIT.java`, `SecurityIT.java`, `EmailRetrySchedulerIT.java`)

**Structure:**
```
src/test/java/com/softropic/sendam/
├── config/                          # Shared test infrastructure
│   ├── TestConfig.java              # @TestConfiguration with Testcontainers, mocks
│   ├── CustomPostgresContainer.java # UTC-configured Postgres container
│   └── ApplicationNoSecurity.java  # Security-disabled test config
├── common/
│   ├── HttpTestClient.java          # HTTP test helper (RestTemplate wrapper)
│   ├── TestClockProvider.java       # Clock manipulation for time-sensitive tests
│   └── TransactionExceptionSimulator.java
├── utils/
│   ├── TestMailManager.java         # In-memory mail capture fake
│   ├── DbCleaner.java
│   └── sql/                         # Custom SQL assertion utilities
│       ├── EntityFetchAsserter.java # JPA lazy/eager load assertions
│       ├── QueryRecorderListener.java
│       └── matcher/
├── security/
│   ├── SecurityIT.java              # Full HTTP login/auth flow tests
│   ├── SecurityFilterChainIT.java
│   ├── api/
│   ├── infrastructure/jwt/
│   ├── repo/
│   └── service/
└── email/
    └── infrastructure/

src/test/resources/
├── sql/
│   ├── createSchema.sql    # Schema bootstrap (run by Testcontainers init)
│   ├── userData.sql        # User seed data
│   ├── authorityData.sql   # Authority/role seed data
│   ├── secData.sql         # Security key seed data
│   ├── cleanup.sql         # Post-test cleanup
│   └── dropAllTables.sql
└── instancio.properties    # Instancio configuration
```

## Test Structure

**Integration Test Suite Organization:**
```java
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {"enable.test.mail=true"})
@Import(TestConfig.class)
@Sql({UserServiceIT.SEC_DATA_SQL_PATH})          // Run before every test method
class UserServiceIT {

    public static final String SEC_DATA_SQL_PATH = "/sql/secData.sql";

    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate template;

    @AfterEach
    void tearDown() {
        template.execute(status -> {
            jdbcTemplate.execute("delete from main.sec");
            jdbcTemplate.execute("delete from main.user");
            return 0;
        });
    }

    @Test
    @Sql({AUTHORITY_SQL_PATH, USER_DATA_SQL_PATH, SEC_DATA_SQL_PATH})
    void createUser() {
        // arrange → act → assert
    }
}
```

**Unit Test Suite Organization:**
```java
class LoginAttemptsServiceTest {

    private LoginAttemptsService loginAttemptsService;

    @BeforeEach
    void setUp() {
        loginAttemptsService = new LoginAttemptsService(defaultDecisionVoter, Ticker.systemTicker());
        resetTestRequestMetadataProvider();
    }

    @AfterEach
    void tearDown() {
        resetTestRequestMetadataProvider();
    }

    @Test
    void givenClientUser_whenMaxAttemptsExceeded_thenBlocked() { ... }
}
```

**Test Method Naming Convention:**
- Integration tests: `verbNoun()` or `verbNounAction()`: `createUser()`, `activateUser()`, `loginWith2FAWhenAccountEnabled()`
- Unit tests: Given-When-Then naming: `givenClientUser_whenMaxAttemptsExceeded_thenBlocked()`
- Both styles are present; unit tests prefer BDD naming

**Parameterized Tests:**
```java
@ParameterizedTest
@EnumSource(value = Credentials.class, names = {"INVALID_EMAIL", "ARBITRARY_EMAIL"})
void loginWithWrongCredentials(Credentials credentials) { ... }
```

## Mocking

**Framework:** Mockito

**Unit test pattern (no Spring context):**
```java
class JwtManagerImplTest {

    @Mock
    private SecretService secretService;

    private JwtManagerImpl jwtManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(secretService.fetchSecret(anyString(), anyString())).thenReturn(secret);
        // Manually construct system under test with mocked dependencies
        jwtManager = new JwtManagerImpl(tokenCreator, tokenValidator, claimsExtractor, jwtConfiguration);
    }

    @AfterEach
    void tearDown() {
        reset(secretService);
    }
}
```

**Static mocking pattern:**
```java
try (MockedStatic<RequestMetadataProvider> mockedProvider = mockStatic(RequestMetadataProvider.class)) {
    mockedProvider.when(RequestMetadataProvider::getClientInfo).thenReturn(mockMetadata);
    // ... exercise code
}
```

**Spring integration test mocking — replace beans with fakes:**
- Register replacement beans in `TestConfig` with `@Primary` and `@ConditionalOnProperty`
- `TestMailManager` replaces real `MailManager` when `enable.test.mail=true`
- `TestClockProvider` replaces real `ClockProvider` for time-manipulation

**What to Mock:**
- External I/O (mail, external HTTP clients)
- Static utility methods (`RequestMetadataProvider`, `ClockProvider`)
- Dependencies not under test in pure unit tests

**What NOT to Mock:**
- Database (use Testcontainers with a real PostgreSQL instance)
- Spring Security filter chain (use full `@SpringBootTest` for security tests)
- The class under test itself

## Fixtures and Factories

**Test Data SQL Scripts:**
```sql
-- src/test/resources/sql/userData.sql  — pre-loaded known users
-- src/test/resources/sql/authorityData.sql — pre-loaded roles
-- src/test/resources/sql/secData.sql   — pre-loaded JWT secrets
-- src/test/resources/sql/cleanup.sql   — post-test cleanup
```

**SQL scripts applied via `@Sql`:**
```java
// Class-level: runs before every test
@Sql({UserServiceIT.SEC_DATA_SQL_PATH})

// Method-level: additional data for a single test
@Test
@Sql({AUTHORITY_SQL_PATH, USER_DATA_SQL_PATH, SEC_DATA_SQL_PATH})
void createUser() { ... }

// Explicit cleanup after each test method:
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
```

**Programmatic cleanup in `@AfterEach`:**
```java
@AfterEach
void tearDown() {
    template.execute(status -> {
        jdbcTemplate.execute("delete from main.sec");
        jdbcTemplate.execute("delete from main.user_addresses");
        jdbcTemplate.execute("delete from main.user_authority");
        jdbcTemplate.execute("delete from main.authority");
        jdbcTemplate.execute("delete from main.user");
        return 0;
    });
}
```

**Instancio for random object creation:**
```java
final Address address = Instancio.create(Address.class);
address.setName("HOME");  // Override specific fields after generation
```

**Manual builder pattern for known test principals:**
```java
private Principal createPrincipal() {
    return new Principal.Builder()
        .username("me@yahoo.com")
        .password("$2a$10$Sdo/...")
        .authorities(Set.of(new Authority("ROLE_USER")))
        .build();
}
```

**Test fake objects:**
- `TestMailManager` (`src/test/java/com/softropic/sendam/utils/TestMailManager.java`): In-memory `ConcurrentHashMap`-backed mail capture. Access captured emails via `getEnvelope(sendId)`. Clear between tests via `clear()`.
- `TestClockProvider` (`src/test/java/com/softropic/sendam/common/TestClockProvider.java`): Static clock manipulation for tests involving time-sensitive logic (JWT expiry, rate limiting).
- `FakeTicker` (Guava `guava-testlib`): Used in `LoginAttemptsServiceTest` to advance time in cache-expiry tests.

**Location:**
- Test infrastructure helpers: `src/test/java/com/softropic/sendam/utils/`
- SQL seed data: `src/test/resources/sql/`

## Coverage

**Requirements:** No enforced coverage threshold detected.

**View Coverage:**
```bash
./mvnw test jacoco:report    # If Jacoco is configured (not detected in pom.xml)
./mvnw verify                # Produces failsafe reports in target/failsafe-reports/
```

## Test Types

**Unit Tests (`*Test.java`):**
- No Spring context; pure JUnit 5 + Mockito
- Direct instantiation of system under test: `new LoginAttemptsService(voter, ticker)`
- Used for: isolated logic (rate limiting, JWT parsing, login attempt counters, validators)
- Example files: `LoginAttemptsServiceTest`, `RateLimitingServiceTest`, `JwtManagerImplTest`, `JWTAuthenticationFilterTest`

**Integration Tests (`*IT.java`):**
- Full Spring Boot context via `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- Real PostgreSQL via Testcontainers (`postgres:14.18` image)
- `@Import(TestConfig.class)` on every integration test class
- `@ActiveProfiles("dev")` activates dev configuration
- Used for: repository queries, service layer with DB, full HTTP flows, email retry, concurrent DB locking
- Example files: `UserServiceIT`, `SecurityIT`, `EmailRetrySchedulerIT`, `UserRepositoryIT`

**E2E Tests:**
- No dedicated E2E framework detected; full HTTP stack tested through `SecurityIT` using `HttpTestClient` (RestTemplate wrapper) against `RANDOM_PORT` server

## Common Patterns

**Async Testing (waiting for async events):**
```java
import static org.awaitility.Awaitility.await;

// Wait until TestMailManager receives a sent email
await().until(() -> testMailManager.getEnvelope(helpCode) != null);
```

**Error Testing:**
```java
// AssertJ for exception assertions:
assertThatThrownBy(() -> httpTestClient.makeHttpRequest(uri, POST, body, headers, Map.class))
    .isInstanceOf(HttpClientErrorException.class)
    .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);

// With additional response body inspection:
assertThatThrownBy(() -> ...)
    .isInstanceOf(HttpClientErrorException.class)
    .satisfies(e -> {
        HttpClientErrorException ex = (HttpClientErrorException) e;
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponseBodyAsString()).contains("security.accLocked");
    });
```

**Security Context Setup (for service-layer tests):**
```java
private void initSecurityContext() {
    final Principal principal = createPrincipal();
    var token = new UsernamePasswordAuthenticationToken(
        principal.getUsername(), null, principal.getAuthorities());
    token.setDetails(principal);
    SecurityContextHolder.setContext(new SecurityContextImpl(token));
}

@AfterEach
void tearDown() {
    SecurityContextHolder.clearContext();
}
```

**Concurrent DB Locking Tests:**
```java
// Pattern used in EmailRetrySchedulerIT to verify SELECT FOR UPDATE SKIP LOCKED
CountDownLatch rowLocked   = new CountDownLatch(1);
CountDownLatch releaseLock = new CountDownLatch(1);
ExecutorService executor   = Executors.newFixedThreadPool(2);

Future<List<EnvelopeEntity>> txA = executor.submit(() ->
    transactionTemplate.execute(status -> {
        List<EnvelopeEntity> locked = repo.fetchFailedEmails();
        rowLocked.countDown();
        releaseLock.await(5, TimeUnit.SECONDS);
        return locked;
    })
);

rowLocked.await(5, TimeUnit.SECONDS);
List<EnvelopeEntity> skipped = transactionTemplate.execute(status -> repo.fetchFailedEmails());
assertThat(skipped).isEmpty();

releaseLock.countDown();
executor.shutdown();
```

**JPA Fetch Type Assertions:**
```java
// Custom EntityFetchAsserter from src/test/java/com/softropic/sendam/utils/sql/EntityFetchAsserter.java
@Autowired
private EntityFetchAsserter entityFetchAsserter;

entityFetchAsserter.assertThat(user)
    .isLazyLoaded("authorities")
    .isEagerlyLoaded("addresses");
```

**Testcontainers Configuration:**
```java
// In TestConfig.java — single shared container per test run
@Bean
@ServiceConnection
PostgreSQLContainer<?> postgresContainer(@Value("${spring.application.name}") String dbName) {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.18"))
        .withDatabaseName(dbName)
        .withPassword("postgres")
        .withUsername("postgres")
        .withInitScript("sql/createSchema.sql");
}
```

---

*Testing analysis: 2026-03-09*
