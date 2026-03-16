# Running Tests

Sendam has two independent test suites:

- **Backend** — JUnit 5 tests (unit + integration) powered by Spring Boot Test and Testcontainers (PostgreSQL)
- **Frontend** — Vitest + Vue Test Utils component tests for the Vue 3 / Quasar admin UI

---

## Quick reference

| Goal | Command |
|------|---------|
| All backend tests | `./mvnw test` |
| All backend tests + integration tests | `./mvnw verify` |
| All frontend tests | `cd src/frontend && npm test` |
| All tests (both suites) | `./mvnw verify && cd src/frontend && npm test` |

---

## Backend tests

Run from the project root. Docker must be running — integration tests spin up a PostgreSQL container via Testcontainers.

```bash
# Unit tests only (fast, no Docker required)
./mvnw test

# Unit tests + integration tests
./mvnw verify
```

**Test counts (as of v1.2):** 26 unit test classes (`*Test.java`), 13 integration test classes (`*IT.java`).

Integration tests (`*IT.java`) use `@SpringBootTest` and a real PostgreSQL instance managed by Testcontainers. They require Docker on the host.

To skip tests during a build (not recommended):

```bash
./mvnw verify -DskipTests
```

---

## Frontend tests

Run from the `src/frontend` directory.

```bash
cd src/frontend
npm test
```

Or from the project root:

```bash
cd src/frontend && npm test
```

**Test counts (as of v1.2):** 13 test files, 72 tests.

### Additional modes

```bash
# Watch mode — re-runs tests on file save
npm run test:watch

# Browser-based UI with test explorer
npm run test:ui
```

Test files live in `src/frontend/test/vitest/__tests__/`. The setup file at `test/vitest/setup-file.js` mocks the Axios boot module to prevent the Quasar virtual module crash in jsdom.

---

## Running both suites together

From the project root:

```bash
./mvnw verify && cd src/frontend && npm test
```

This runs the full backend suite (unit + integration) first, then the full frontend suite. If the backend fails, the frontend suite is not reached.

To run them in parallel (requires two terminals):

```
# Terminal 1
./mvnw verify

# Terminal 2
cd src/frontend && npm test
```

---

## Maven build integration

The `frontend-maven-plugin` already runs `npm install` and `quasar build` during the `generate-resources` phase to compile and bundle the frontend into `target/classes/static`. Frontend **tests** are not wired into the Maven lifecycle by default.

To run frontend tests automatically as part of `mvn verify`, add a new execution to the `frontend-maven-plugin` block in `pom.xml`:

```xml
<execution>
    <id>npm test</id>
    <goals>
        <goal>npm</goal>
    </goals>
    <phase>test</phase>
    <configuration>
        <arguments>test</arguments>
    </configuration>
</execution>
```

Place this execution after the existing `npm install` execution and before the `npx quasar build` execution. With this in place, `./mvnw verify` runs all backend unit tests, all backend integration tests, and all frontend Vitest tests in a single command.

> **Note:** The `test` phase runs before `package`, so frontend tests will block the build if they fail — which is the desired behaviour in CI.

---

## CI environment notes

- Docker must be available for backend integration tests (Testcontainers launches a `postgres:16` container).
- Node.js `v22.16.0` and npm `11.4.2` are downloaded automatically by `frontend-maven-plugin` into a local `.node` directory — no pre-installed Node required when building via Maven.
- When running the frontend suite standalone (`npm test`), Node must be installed separately on the host.
