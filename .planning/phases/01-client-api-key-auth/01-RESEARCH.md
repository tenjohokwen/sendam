# Phase 1: Client & API Key Authentication - Research

**Researched:** 2026-03-10
**Domain:** Spring Security multi-chain, API key hashing, Bucket4j rate limiting
**Confidence:** HIGH (codebase verified) / HIGH (official docs) / MEDIUM (pattern guidance)

---

## Summary

This phase adds a second, completely separate authentication mechanism to an application that already has JWT-based auth for portal/admin users. The new mechanism authenticates programmatic API clients via Bearer API keys sent in the `Authorization` header. The two mechanisms must coexist: JWT auth continues to protect existing `/api/**` and `/v1/**` paths; the new API-key filter chain takes over `/v1/**` programmatic-client paths.

The project already uses Bucket4j 8.10.1 in-memory rate limiting via an AOP aspect (`RateLimitingAspect`). The existing `RateLimitingService` uses `Refill.intervally` and `ConcurrentHashMap<String, Bucket>` — this infrastructure must be extended, not rebuilt, for the two new rate limits (10 req/s per client, 1000 recipients/min per client).

API key storage follows the industry-standard prefix-plus-hash pattern: a short random prefix is stored in plaintext for fast DB lookup, the remainder is hashed with HMAC-SHA256 (not BCrypt) and stored. The full raw key is returned only at creation.

**Primary recommendation:** Use a second `@Order(1)` `SecurityFilterChain` scoped to `/v1/**` that runs a custom `OncePerRequestFilter` (modelled on `JWTAuthorizationFilter`) to authenticate Bearer API keys, and extend the existing `RateLimitingService` / `RateLimitingAspect` for per-client rate limiting.

---

## Standard Stack

All libraries are already present in `pom.xml`. No new dependencies are required.

### Core (already in pom.xml)
| Library | Version | Purpose | Status |
|---------|---------|---------|--------|
| spring-boot-starter-security | 3.5.11 (managed) | Multi-chain security config | Already used |
| bucket4j-core | 8.10.1 | Per-client token-bucket rate limiting | Already used |
| spring-boot-starter-aop | managed | `@RateLimited` aspect | Already used |
| spring-boot-starter-data-jpa | managed | Client + ApiKey entities | Already used |
| flyway-core | managed | DB migrations | Already used |
| commons-codec | present | HMAC-SHA256 via `DigestUtils` / `HmacUtils` | Already used |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| hypersistence-utils-hibernate-63 | present | `@Tsid` TSID primary keys (already in `BaseEntity`) | Use for new entities |
| lombok | managed | `@SuperBuilder`, `@NoArgsConstructor` (already in base entities) | Use for new entities |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `OncePerRequestFilter` | Spring's `BearerTokenAuthenticationFilter` (OAuth2 resource server) | OAuth2 RS requires `oauth2ResourceServer()` DSL and JWT/opaque token infrastructure — overkill for a simple API key; stay with custom filter |
| HMAC-SHA256 key hash | BCrypt | BCrypt is intentionally slow (designed for passwords); API key verification runs on every request — BCrypt would add 200–500ms per request. HMAC-SHA256 with a secret pepper is fast and cryptographically sound for this use case |
| In-memory Bucket4j | Redis + Bucket4j (distributed) | For a single-instance deployment, in-memory is correct and already proven in the codebase |

**Installation:** No new Maven dependencies needed.

---

## Architecture Patterns

### Recommended Package Structure

The new code lives under a new top-level module `client` (sibling to `security`, `email`, `common`):

```
src/main/java/com/softropic/sendam/
├── security/                        # EXISTING — JWT auth for portal users
│   ├── config/SecurityConfiguration.java   # MODIFY: add @Order, new chain
│   └── ...
└── client/                          # NEW top-level module
    ├── api/
    │   ├── AdminClientResource.java         # POST /admin/clients
    │   └── ClientApiKeyResource.java        # POST/GET/DELETE /v1/apikeys
    ├── service/
    │   ├── ClientService.java               # create client, issue first key
    │   └── ApiKeyService.java               # create, list, revoke keys
    ├── repo/
    │   ├── ClientEntity.java                # @Entity client_account table
    │   ├── ClientRepository.java
    │   ├── ClientApiKeyEntity.java          # @Entity client_api_key table
    │   └── ClientApiKeyRepository.java
    ├── infrastructure/
    │   └── filter/
    │       └── ApiKeyAuthenticationFilter.java  # OncePerRequestFilter
    └── contract/
        ├── CreateClientRequest.java
        ├── CreateApiKeyResponse.java
        └── ApiKeyDto.java
```

### Pattern 1: Second SecurityFilterChain with `@Order`

The existing `SecurityConfiguration.filterChain()` bean has no `@Order` — Spring Boot assigns it a default order. The new chain for `/v1/**` API key paths must be registered first (lower order number = higher priority).

**Critical constraint from codebase:** `AppEndpoints.SECURED` is already `"/v1/**"` and `AppEndpoints.PUBLIC_ENDPOINTS` already permits some `/v1/account/**` paths. The new client-facing API uses the same `/v1/**` prefix. This means the new chain must claim `/v1/**` at a lower order so it runs before the existing JWT chain, OR the existing chain must be restructured. The cleanest approach: give the existing chain `@Order(2)` and create the new API-key chain at `@Order(1)` scoped to `/v1/api/**` (a new sub-prefix for client-facing endpoints) so there is no overlap.

**Recommended URL layout:**
- `/v1/api/**` — Client-facing API (new Bucket4j-rate-limited, API-key authenticated chain)
- `/admin/clients/**` — Admin operations (guarded by existing JWT ROLE_ADMIN)
- `/v1/**` (existing) — Portal/user routes (existing JWT chain, unchanged)

```java
// Source: Spring Security docs (docs.spring.io/spring-security/reference/servlet/architecture.html)
// In SecurityConfiguration (or a new @Configuration class)

@Bean
@Order(1)
public SecurityFilterChain clientApiSecurityFilterChain(
        HttpSecurity http,
        ApiKeyAuthenticationFilter apiKeyFilter) throws Exception {
    http
        .securityMatcher("/v1/api/**")          // only client-facing API paths
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .anyRequest().authenticated()
        )
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(/* 403 JSON handler */))
        .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
    return http.build();
}

// Existing bean gets explicit @Order(2)
@Bean
@Order(2)
public SecurityFilterChain filterChain(HttpSecurity http, ...) throws Exception {
    // ... unchanged ...
}
```

### Pattern 2: ApiKeyAuthenticationFilter

Model after `JWTAuthorizationFilter` (which extends `OncePerRequestFilter`). The filter:

1. Extracts the `Authorization: Bearer <key>` header.
2. If absent: throws `MissingAuthenticationException` (or returns 401 — depending on `authenticationEntryPoint` config).
3. Splits the key into prefix (first N chars) and secret (remainder).
4. Looks up `ClientApiKeyEntity` by prefix from DB.
5. Verifies HMAC-SHA256(secret, pepper) == stored hash in constant time.
6. Checks `status == ACTIVE`.
7. Sets `UsernamePasswordAuthenticationToken` on `SecurityContextHolder` with `clientId` as principal and `ROLE_API_CLIENT` as authority.

```java
// Source: modelled on existing JWTAuthorizationFilter in codebase
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            // fall through — security config denies unauthenticated requests
            chain.doFilter(request, response);
            return;
        }
        String rawKey = header.substring(7);
        try {
            Authentication auth = apiKeyService.authenticate(rawKey);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (AuthorizationException e) {
            // delegate to HandlerExceptionResolver like SecurityAdviceFilter does
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

### Pattern 3: API Key Generation (prefix + HMAC-SHA256 hash)

```
raw key format: "snd_" + prefix(8 chars) + secret(32 chars)
  e.g.: snd_a1b2c3d4ExampleSecretValue12345
              ^^^^^^^^  <- stored in plaintext for lookup
                        ^^^^^^^^^^^^^^^^^^^^^^^^ <- HMAC-SHA256'd, stored hashed
```

**Creation algorithm:**
1. Generate 8-byte random prefix using `SecureRandom` → hex-encode → 16 hex chars.
2. Generate 32-byte random secret → base64url-encode → ~43 chars.
3. Concatenate as `snd_<prefix><secret>`.
4. Return full raw key to caller (shown ONCE only).
5. Store: `key_prefix = "snd_<prefix>"`, `key_hash = HmacUtils.hmacSha256Hex(serverPepper, secret)`.

**Lookup + verification algorithm (on each request):**
1. Receive `Authorization: Bearer snd_<prefix><secret>`.
2. Strip `Bearer `, extract prefix (`snd_` + first 16 chars = 20 chars total).
3. `SELECT * FROM client_api_key WHERE key_prefix = ?` — O(1) indexed lookup.
4. Recompute `HmacUtils.hmacSha256Hex(serverPepper, incomingSecret)`.
5. Compare with stored `key_hash` using `MessageDigest.isEqual()` (constant time).
6. If mismatch or status != ACTIVE → 403.

**Why not BCrypt:** BCrypt takes ~200-500ms per verification. With 10 req/s per client, this is unacceptable. HMAC-SHA256 is microseconds. The security trade-off is acceptable for API keys (which are long, random, and not user-memorizable).

### Pattern 4: Rate Limiting Extension

The existing `RateLimitingService` stores buckets in a `ConcurrentHashMap<String, Bucket>` keyed by `"limitKey:identifier"`. Buckets use `Refill.intervally`.

Two rate limits are required:

| Limit | Bucket key | Capacity | Window | Unit |
|-------|-----------|----------|--------|------|
| 10 req/s | `"api_req:<client_id>"` | 10 | 1 | SECONDS |
| 1000 recipients/min | `"sms_rcpt:<client_id>"` | 1000 | 1 | MINUTES |

**Key insight:** The recipient count limit is not 1 token per request — an SMS send with 50 recipients consumes 50 tokens. The existing `tryConsume(1)` must be extended to `tryConsume(recipientCount)`. Bucket4j's `Bucket.tryConsume(long tokensToConsume)` already supports this.

For 10 req/s use `Refill.greedy(10, Duration.ofSeconds(1))` — greedy distributes tokens smoothly across the second (avoids burst-then-block within window). The existing codebase uses `Refill.intervally` which refills all tokens at once after the window — acceptable for minutes but harsh for 1-second windows.

```java
// 10 req/s with smooth distribution (greedy)
Refill refill = Refill.greedy(10, Duration.ofSeconds(1));
Bandwidth limit = Bandwidth.classic(10, refill);

// 1000 recipients/min (intervally is fine for longer windows)
Refill rcptRefill = Refill.intervally(1000, Duration.ofMinutes(1));
Bandwidth rcptLimit = Bandwidth.classic(1000, rcptRefill);
```

The `RateLimitingAspect` currently uses the client IP as identifier (`RequestMetadataProvider.getClientInfo().getIpAddress()`). For API-key-authenticated requests, the identifier must be `client_id` derived from the authenticated principal, not IP. The aspect needs an updated `getIdentifier()` method that checks whether the `SecurityContext` contains an API-client principal.

### Pattern 5: X-Request-ID Response Header

`RequestIdProvider` already generates and stores a UUID request ID per thread via `MDC`. The constant `Constants.REQUEST_ID_HEADER_NAME = "X-Request-Id"` and `SecurityAdviceFilter` already calls `RequestMetadataProvider.initRequestMetadata(request)` which calls `RequestIdProvider.addReqIdToThread(request)`.

What is missing: the request ID is **read** from the incoming request (if present) but never **written back** to the response. A `HandlerInterceptor` or a response-writing `OncePerRequestFilter` must copy the MDC value into `response.addHeader("X-Request-ID", requestId)` after every response.

The cleanest approach: add a `ResponseHeaderFilter` (separate `OncePerRequestFilter`) registered **outside** any `SecurityFilterChain` via `FilterRegistrationBean` so it applies to all requests regardless of chain. Add it in `SecurityConfiguration` alongside the existing `ForwardedHeaderFilter`.

```java
// Source: pattern from SecurityConfiguration.forwardedHeaderFilter()
@Bean
public FilterRegistrationBean<RequestIdResponseFilter> requestIdResponseFilter() {
    FilterRegistrationBean<RequestIdResponseFilter> reg =
        new FilterRegistrationBean<>(new RequestIdResponseFilter());
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return reg;
}

// Filter impl:
public class RequestIdResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        chain.doFilter(req, res);
        // MDC value set by SecurityAdviceFilter earlier in chain
        String reqId = MDC.get(Constants.REQUEST_ID_NAME);
        if (reqId != null) {
            res.addHeader("X-Request-ID", reqId);
        }
    }
}
```

**Note:** The incoming header is named `X-Request-Id` (mixed case) per `Constants.REQUEST_ID_HEADER_NAME`. The requirement calls for `X-Request-ID` in the response. Use `X-Request-ID` in the response — HTTP headers are case-insensitive.

### Pattern 6: Admin Endpoint Authentication

`AppEndpoints.SECURED_MAPPINGS` includes `/api/**` mapped to `[ROLE_ADMIN, ROLE_LTD_ADMIN, ROLE_USER]` and `/manage/**` mapped to `[ROLE_ADMIN]`. Admin endpoints like `POST /admin/clients` should be placed under `/api/admin/**` (prefix `/api/`) so they are automatically guarded by the **existing** JWT filter chain (`JWTAuthorizationFilter`) which already checks for `ROLE_ADMIN`.

Do NOT place admin endpoints at `/v1/api/admin/**` — that would put them in the new API-key chain, which uses API key auth (not JWT). Place them at `/api/admin/**` (existing JWT chain) and add the appropriate authority check to `AppEndpoints.SECURED_MAPPINGS`.

### Anti-Patterns to Avoid

- **Don't reuse the `allowed.clients` whitelist for API keys:** The existing `ClientIdAccessDecisionManager` checks a static list from `application.yaml` (`allowed.clients`). That is for internal machine-client allowlisting — a different concept. New programmatic clients are authenticated dynamically via DB lookup.
- **Don't use BCrypt for API key hashing:** It is too slow for per-request verification. Use HMAC-SHA256.
- **Don't use `Refill.intervally` for the 10 req/s bucket:** It will allow 10 requests instantly at the start of each second. Use `Refill.greedy` for smooth distribution.
- **Don't put client_id in the request:** The filter derives `client_id` from the authenticated API key. Never trust a client-supplied `client_id`.
- **Don't apply the `@RateLimited` aspect with IP-based identifier for API key requests:** When an API-key auth principal is set in SecurityContext, the rate limiter must use `client_id` as the identifier, not IP.
- **Don't register `ApiKeyAuthenticationFilter` as a `@Component` AND add it to a `FilterRegistrationBean`:** Spring Boot auto-registers `@Component` filters. Either use `@Component` and add to the chain via `addFilterBefore`, or use a `FilterRegistrationBean` — not both. Since the codebase uses `@Component` for `SecurityAdviceFilter`, follow that pattern but ensure the filter is NOT auto-registered for all paths (use `setEnabled(false)` on a `FilterRegistrationBean` if needed to prevent double registration).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Token-bucket rate limiting | Custom counter with `AtomicLong` + TTL | `bucket4j-core` 8.10.1 (already in pom) | Handles concurrency, refill math, edge cases |
| HMAC-SHA256 computation | `javax.crypto.Mac` boilerplate | `org.apache.commons:commons-codec` `HmacUtils.hmacSha256Hex(key, data)` — already in pom | Already imported, tested |
| Constant-time comparison | `String.equals()` | `java.security.MessageDigest.isEqual(byte[], byte[])` | Timing-safe, prevents timing attacks |
| Secure random key generation | `Math.random()` or `UUID.randomUUID()` | `java.security.SecureRandom` | Cryptographically secure |
| Entity ID generation | `@GeneratedValue(AUTO)` | `@Tsid` from `hypersistence-utils` — already in `BaseEntity` | Consistent with codebase pattern |
| DB schema versioning | Hibernate `ddl-auto: update` | Flyway migration script | Already the project standard (`ddl-auto: none`) |

**Key insight:** BCrypt (`BCryptPasswordEncoder`) is already a Spring bean in `SecurityConfiguration`. Do NOT reuse it for API key hashing — it is reserved for passwords. API key hashing requires HMAC with a server-side secret, not BCrypt.

---

## Common Pitfalls

### Pitfall 1: Double Filter Registration
**What goes wrong:** `ApiKeyAuthenticationFilter` annotated `@Component` gets registered by Spring Boot as a global servlet filter AND added to the security chain via `addFilterBefore`, causing every request to be processed twice.
**Why it happens:** Spring Boot auto-registers any `@Component` that extends `OncePerRequestFilter` as a servlet filter.
**How to avoid:** Either (a) do NOT annotate the filter with `@Component` — instantiate it manually and inject dependencies via constructor, as `JWTAuthorizationFilter` does; or (b) annotate with `@Component` and add a `FilterRegistrationBean<ApiKeyAuthenticationFilter>` with `setEnabled(false)` to suppress auto-registration.
**Warning signs:** Same request appears twice in logs; rate limits fire at half the expected rate.

### Pitfall 2: SecurityFilterChain Ordering Conflict
**What goes wrong:** The new `/v1/api/**` chain has the same or higher order number than the existing chain, which already claims `/v1/**`. The existing chain intercepts the request first.
**Why it happens:** Default `@Order` on unordered beans is `Integer.MAX_VALUE`; if both chains match `/v1/**`, the one with lower order wins.
**How to avoid:** Assign the existing `filterChain()` bean an explicit `@Order(2)`. Assign the new client API chain `@Order(1)` with `securityMatcher("/v1/api/**")`. Verify with an IT test.
**Warning signs:** API key requests return 403 from JWT auth filter rather than from the API key filter.

### Pitfall 3: RateLimitingAspect Identifier Uses IP Instead of client_id
**What goes wrong:** The existing `RateLimitingAspect.getClientIdentifier()` returns `RequestMetadataProvider.getClientInfo().getIpAddress()`. For API-key clients, two clients behind the same NAT share a bucket.
**Why it happens:** The aspect was originally written for IP-based limiting.
**How to avoid:** After the API key filter sets the `SecurityContext` principal, update `getClientIdentifier()` to check `SecurityContextHolder.getContext().getAuthentication()` — if the principal is an API-client type, use `client_id`; otherwise fall back to IP.
**Warning signs:** Rate limiting test shows shared bucket across different client IDs from the same IP.

### Pitfall 4: Recipient Count Rate Limit Needs `tryConsume(N)` Not `tryConsume(1)`
**What goes wrong:** Each SMS send call calls `tryConsume(1)` regardless of recipient count, so the 1000-recipients/min limit is never enforced correctly.
**Why it happens:** The `@RateLimited` annotation and aspect are designed for per-call limiting, not per-unit limiting.
**How to avoid:** Either (a) add a new `@RateLimitedWithCount` annotation that accepts a runtime count, or (b) call `rateLimitingService.tryConsume(identifier, key, capacity, duration, unit, recipientCount)` programmatically in the SMS send service. The Bucket4j `Bucket.tryConsume(tokensToConsume)` method accepts any long value.
**Warning signs:** Client can send 1 million recipients in 1 minute as long as each request has at most 1000 recipients.

### Pitfall 5: Raw Key Exposure in Logs
**What goes wrong:** The raw API key appears in server logs via `RequestMetadataProvider`'s `RequestMetadata.toString()` which already logs `apiKey`.
**Why it happens:** `RequestMetadataProvider.initRequestMetadata()` calls `requestMetadata.setApiKey(request.getHeader(API_KEY_HEADER))` — but `API_KEY_HEADER = "X-Client-Id"` (not `Authorization`). The Bearer token in `Authorization` header is not currently captured in `RequestMetadata`.
**How to avoid:** Do NOT add the raw Bearer token value to `RequestMetadata`. Add only the derived `client_id` after authentication. Also ensure `BodySanitizer.sanitize()` strips `Authorization` headers in `LoggingFilter`.
**Warning signs:** API key values visible in structured log output.

### Pitfall 6: Revoked Key Cached in SecurityContext
**What goes wrong:** A revoked key continues to authenticate if the DB result is cached.
**Why it happens:** Spring's `@Cacheable` or application-level caching might be added later, returning stale `ACTIVE` status.
**How to avoid:** Do not cache API key lookups. Each request must do a DB read. The requirement states "revoked keys immediately lose access" (AUTH-04) — caching violates this. Add a note in `ApiKeyService` / `ClientApiKeyRepository`.

---

## Code Examples

### Creating an API Key (Service Layer)

```java
// Source: industry pattern (prefix.dev blog, dennisokeeffe.com/blog), verified against Bucket4j docs
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import java.security.SecureRandom;
import java.util.Base64;

public ApiKeyCreationResult createApiKey(Long clientId, String label) {
    SecureRandom rng = new SecureRandom();

    // Prefix: 8 random bytes → 16 hex chars (used for fast DB lookup)
    byte[] prefixBytes = new byte[8];
    rng.nextBytes(prefixBytes);
    String prefix = HexFormat.of().formatHex(prefixBytes); // 16 chars

    // Secret: 32 random bytes → base64url without padding
    byte[] secretBytes = new byte[32];
    rng.nextBytes(secretBytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes); // ~43 chars

    // Full raw key shown to user ONCE
    String rawKey = "snd_" + prefix + secret;  // "snd_" + 16 + 43 = 63 chars

    // Hash: HMAC-SHA256(serverPepper, secret) — store this in DB
    String keyHash = HmacUtils.hmacSha256Hex(serverPepper, secret);

    ClientApiKeyEntity entity = new ClientApiKeyEntity();
    entity.setClientId(clientId);
    entity.setKeyPrefix("snd_" + prefix);    // stored for lookup
    entity.setKeyHash(keyHash);               // stored for verification
    entity.setLabel(label);
    entity.setStatus(EntityStatus.ACTIVE);
    repository.save(entity);

    return new ApiKeyCreationResult(entity.getId(), rawKey /* show once */);
}
```

### Verifying an API Key (Filter)

```java
// Source: pattern from JWTAuthorizationFilter in codebase + prefix.dev pattern
public Authentication authenticate(String rawKey) {
    if (!rawKey.startsWith("snd_") || rawKey.length() < 20) {
        throw new AuthorizationException("Invalid key format", SecurityError.MISSING_TOKEN);
    }
    // Extract prefix (first 20 chars including "snd_")
    String prefix = rawKey.substring(0, 20);         // "snd_" + 16 chars
    String incomingSecret = rawKey.substring(20);    // remaining chars

    ClientApiKeyEntity keyEntity = repository.findByKeyPrefix(prefix)
        .orElseThrow(() -> new AuthorizationException("Key not found", SecurityError.MISSING_TOKEN));

    if (keyEntity.getStatus() != EntityStatus.ACTIVE) {
        throw new AuthorizationException("Key revoked", SecurityError.MISSING_RIGHTS);
    }

    String expectedHash = HmacUtils.hmacSha256Hex(serverPepper, incomingSecret);
    // Constant-time comparison prevents timing attacks
    if (!MessageDigest.isEqual(expectedHash.getBytes(), keyEntity.getKeyHash().getBytes())) {
        throw new AuthorizationException("Key mismatch", SecurityError.MISSING_TOKEN);
    }

    // Set principal = clientId, authority = ROLE_API_CLIENT
    return new UsernamePasswordAuthenticationToken(
        keyEntity.getClientId(), null,
        List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
    );
}
```

### Bucket4j Rate Limit Configuration (10 req/s)

```java
// Source: Bucket4j 8.x API — codebase already uses Bucket, Bandwidth, Refill (RateLimitingService.java)
// Use Refill.greedy for smooth per-second distribution (not intervally)
Refill refill = Refill.greedy(10, Duration.ofSeconds(1));
Bandwidth limit = Bandwidth.classic(10, refill);
Bucket bucket = Bucket.builder().addLimit(limit).build();
```

### Bucket4j Recipient Count Rate Limit (1000/min consuming N tokens)

```java
// Source: Bucket4j API — tryConsume(long tokensToConsume)
public boolean tryConsumeRecipients(String clientId, int recipientCount) {
    String bucketKey = "sms_rcpt:" + clientId;
    Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createRecipientBucket());
    return bucket.tryConsume(recipientCount);   // consume N tokens, not 1
}

private Bucket createRecipientBucket() {
    Refill refill = Refill.intervally(1000, Duration.ofMinutes(1));
    return Bucket.builder().addLimit(Bandwidth.classic(1000, refill)).build();
}
```

### Flyway Migration Schema (DB entities)

```sql
-- V2__client_api_key.sql
CREATE TABLE main.client_account (
    id            BIGINT PRIMARY KEY,          -- @Tsid generated
    name          VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(50),
    created_date  TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP,
    request_id    VARCHAR(100),
    session_id    TEXT
);

CREATE TABLE main.client_api_key (
    id            BIGINT PRIMARY KEY,          -- @Tsid generated
    client_id     BIGINT NOT NULL REFERENCES main.client_account(id),
    key_prefix    VARCHAR(24) NOT NULL UNIQUE, -- "snd_" + 16 chars
    key_hash      VARCHAR(64) NOT NULL,        -- HMAC-SHA256 hex (64 chars)
    label         VARCHAR(100),
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(50),
    created_date  TIMESTAMP,
    last_modified_by   VARCHAR(50),
    last_modified_date TIMESTAMP,
    request_id    VARCHAR(100),
    session_id    TEXT
);

CREATE INDEX idx_client_api_key_prefix ON main.client_api_key(key_prefix);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single `SecurityFilterChain` with all rules | Multiple `SecurityFilterChain` beans with `@Order` + `securityMatcher` | Spring Security 5.7+ (Spring Boot 3.x) | Clean separation of auth mechanisms |
| `WebSecurityConfigurerAdapter` | Direct `HttpSecurity` bean configuration | Spring Security 5.7 | `WebSecurityConfigurerAdapter` is removed in Spring Security 6 |
| `Refill.of(...)` | `Refill.greedy(...)` / `Refill.intervally(...)` | Bucket4j 5.x+ | Old API deprecated; new API is in use in codebase |
| `Bandwidth.simple(...)` | `Bandwidth.classic(capacity, refill)` | Bucket4j 8.x | `Bandwidth.simple` deprecated in 8.x |

**Deprecated/outdated:**
- `WebSecurityConfigurerAdapter`: Removed in Spring Security 6. This project correctly does not use it.
- `Bandwidth.simple()`: Deprecated in Bucket4j 8.x. The existing `RateLimitingService` correctly uses `Bandwidth.classic()`.
- BCrypt for API keys: Never correct — only for passwords. Do not use `BCryptPasswordEncoder` for API key hashing.

---

## Open Questions

1. **`serverPepper` for HMAC-SHA256**
   - What we know: HMAC requires a secret key. The codebase uses `JwtSecretService` for JWT secrets and `JwtSecretProvider` which likely reads from config or DB.
   - What's unclear: Where the API key pepper should live — application YAML env variable (`${API_KEY_PEPPER}`) or DB-stored secret (like JWT uses).
   - Recommendation: Follow the pattern in `JwtSecretProvider` — store as an environment variable injected via `@Value("${apikey.pepper}")`, not hardcoded. Must be documented in `application.yaml` with a placeholder.

2. **URL prefix for client API vs. portal API conflict**
   - What we know: `AppEndpoints.SECURED = "/v1/**"` and `PUBLIC_ENDPOINTS` already includes `/v1/account/**`. Adding a new chain for `/v1/api/**` requires that the new chain runs before the existing one for that sub-path.
   - What's unclear: Whether existing portal users might accidentally hit `/v1/api/**` paths and be rejected by the wrong chain.
   - Recommendation: Use `/v1/api/**` for client-facing API and add it to the new chain's `securityMatcher`. Write an IT test confirming that `/v1/account/register` still routes to the JWT chain.

3. **Admin endpoint path**
   - What we know: The existing JWT chain secures `/api/**` for `ROLE_ADMIN` / `ROLE_USER`. Admin client creation fits naturally at `/api/admin/clients`.
   - What's unclear: Whether a separate `LTD_ADMIN` sub-role or just `ROLE_ADMIN` should create clients.
   - Recommendation: Start with `ROLE_ADMIN` only for `ADMIN-01`. Extend to `ROLE_LTD_ADMIN` later if needed.

---

## Sources

### Primary (HIGH confidence)
- Codebase (verified directly): `SecurityConfiguration.java`, `RateLimitingService.java`, `RateLimitingAspect.java`, `RateLimited.java`, `AppEndpoints.java`, `JWTAuthorizationFilter.java`, `SecurityAdviceFilter.java`, `RequestMetadataProvider.java`, `RequestIdProvider.java`, `Constants.java`, `SecurityConstants.java`, `BaseEntity.java`, `AbstractAuditingEntity.java`, `ApiAdvice.java`, `pom.xml` (bucket4j-core 8.10.1 confirmed)
- [Spring Security Architecture Docs](https://docs.spring.io/spring-security/reference/servlet/architecture.html) — multiple SecurityFilterChain, FilterChainProxy, @Order, securityMatcher

### Secondary (MEDIUM confidence)
- [prefix.dev blog: How we implemented API keys](https://prefix.dev/blog/how_we_implented_api_keys) — prefix + hash pattern, verified against multiple sources
- [Dennis O'Keeffe: Roll Your Own API Keys](https://www.dennisokeeffe.com/blog/2025-04-07-roll-your-own-api-keys) — HMAC-SHA256 for API key hashing with prefix lookup
- [Harshad Sonawane: Bucket4j rate limiting](https://harshad-sonawane.com/blog/api-rate-limiting-throttling-in-spring-boot-with-bucket4j/) — Bucket4j 8.x API patterns (ConcurrentHashMap, Refill, Bandwidth.classic)

### Tertiary (LOW confidence)
- WebSearch results on Spring Security multiple filter chains (2025) — confirmed the @Order + securityMatcher pattern is current standard

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries confirmed in `pom.xml`
- Architecture (multiple filter chain): HIGH — confirmed by Spring Security official docs + codebase structure
- API key hashing strategy: HIGH — HMAC-SHA256 with prefix lookup is verified industry pattern from multiple sources
- Bucket4j rate limiting extension: HIGH — existing `RateLimitingService` uses identical API; extension is additive
- `Refill.greedy` vs `Refill.intervally`: MEDIUM — documented in Bucket4j GitHub but not directly fetched; recommendation based on algorithm semantics verified from multiple articles
- Recipient-count rate limit (`tryConsume(N)`): HIGH — Bucket4j `tryConsume(long)` is standard API

**Research date:** 2026-03-10
**Valid until:** 2026-06-10 (stable libraries; Spring Security and Bucket4j APIs do not change rapidly in patch/minor versions)
