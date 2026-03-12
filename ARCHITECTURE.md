# Application Architecture

## Overview

The application follows a modular, domain-driven architecture designed to eliminate circular dependencies, enforce unidirectional dependency flow, and ensure high cohesion.

The core guiding principle is: **a package's location is its specification**. You should be able to determine what a class is allowed to import — and what is allowed to import it — purely from the package it lives in.

---

## Domain Modules

The application is divided into top-level modules representing distinct business domains (e.g., `security`, `gateway`, `billing`, `sms`). 

### Module Inter-Communication
To maintain strict decoupling between domain modules, communication must follow these priorities:

1.  **Event-Driven Architecture (EDA) - [HIGHEST PRIORITY]:** Domains should communicate primarily by publishing and consuming events. This ensures that the producer of information has zero knowledge of its consumers. Use Spring `ApplicationEventPublisher` for synchronous or asynchronous decoupling.
2.  **Dependency Inversion / SPI:** When a domain needs to interact with an external technical provider or a specific implementation detail, it should define a **Service Provider Interface (SPI)** within its own `service` or `contract` layer. Implementation modules (e.g., `provider.nexah`) then implement this interface.
3.  **Strict Layering:** Cross-module imports are only permitted from the `contract` or `service` layers of another module. **Prohibited:** Direct imports of `repo`, `entity`, or `infrastructure` classes from another domain module.

---

## Internal Package Structure (Per Module)

Every domain module follows a standardized layered structure:

```
domain/
├── api/              Entry points: REST controllers and facades
├── contract/         Shared language: DTOs, enums, events, exceptions, value objects
├── service/          Domain business logic and SPI interfaces
├── infrastructure/   Technical implementations (External clients, SPI implementations)
├── repo/             Persistence layer: JPA entities and Spring Data repositories
├── common/           Internal cross-cutting utilities
└── config/           Spring wiring and configuration
```

---

## Package Conventions

### Dependency Flow

```
api → service → repo
 ↓       ↓       ↓
      contract ←──
         ↑
   (accessible from everywhere)

infrastructure → service
             └→ repo
             └→ contract
```

Dependencies only flow **downward or toward `contract`**. No package may import from a package above it in this hierarchy.

---

### Package Roles and Access Rules

#### `api`
**Role:** Entry points into the module. Controllers (`@RestController`) and facades that orchestrate service calls and translate between the web layer and domain logic.

**Allowed imports:** `service`, `contract`, `repo`

**Prohibited imports:** `infrastructure`, `config`

---

#### `contract`
**Role:** The shared language of the module. Contains only passive types: DTOs, enums, events, exceptions, and value objects. Has zero logic that depends on Spring or other infrastructure. Can be safely imported from any layer without creating a dependency cycle.

**Allowed imports:** nothing within the same module (standard Java and third-party libraries only)

**Prohibited:** any Spring stereotype annotation (`@Component`, `@Service`, `@Bean`, `@Repository`, `@Controller`, `@Configuration`).

**Sub-packages:**
- `contract/event/` — Domain events published across modules
- `contract/exception/` — Module-specific exceptions
- `contract/util/` — Stateless utility classes and constants

---

#### `service`
**Role:** Domain business logic. Services own use-case orchestration: they read from `repo`, apply business rules, publish events, and return `contract` types. This layer also defines **SPI interfaces** for external integrations.

**Allowed imports:** `repo`, `contract`, `common`

**Prohibited imports:** `infrastructure`, `api`, `config`

---

#### `infrastructure`
**Role:** Technical implementation details that fulfill service interfaces or SPIs. External API clients (e.g., NexahClient), security handlers, or complex technical logic. These classes know *how* things are done; `service` classes know *what* needs to be done.

**Allowed imports:** `service`, `repo`, `contract`, `common`

**Prohibited imports:** `api`

---

#### `repo`
**Role:** Persistence leaf node. JPA entities, Spring Data repository interfaces, and entity listeners.

**Allowed imports:** `contract` only

**Prohibited imports:** everything else within the module

---

#### `common`
**Role:** Internal cross-cutting utilities shared across layers that are too tightly coupled to this module to live in a general `common` module.

**Allowed imports:** `contract` only

---

#### `config`
**Role:** Spring composition root. Wires all beans together. `@Configuration` classes here are permitted to import from all other layers to perform wiring.

---

## Dependency Rule Enforcement Checklist

When adding or moving a class, verify:

- [ ] **EDA Priority:** Can this cross-module communication be handled via an Event instead of a direct Service call?
- [ ] **Domain Isolation:** Does this class import a `repo` or `entity` from another domain? (If yes, refactor to use an Event or Service).
- [ ] `repo/` classes import **only** from `contract/` and within `repo/`
- [ ] `service/` classes import **only** from `repo/`, `contract/`, `common/`
- [ ] `contract/` classes carry **no** Spring stereotype annotations
- [ ] `@ConfigurationProperties` classes live in `contract/` with `@Configuration` stripped
