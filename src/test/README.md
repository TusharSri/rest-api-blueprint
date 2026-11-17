# Test suite

| Layer | Package | What it proves | Spring context |
|---|---|---|---|
| Unit | `unit.service` | Business rules: paging, sorting, filtering, create/replace/patch/delete, auth logic | none |
| Unit | `unit.repository` | Thread safety: unique ids, SKU uniqueness under concurrency | none |
| Unit | `unit.idempotency` | Replay, payload mismatch (422), failed actions not stored, exactly-once under concurrency | none |
| Unit | `unit.security` | JWT claims, lifetime, tamper and wrong-key rejection | none |
| Unit | `unit.exception` | Status code + error envelope for every exception type | none |
| Unit | `unit.dto` | Response envelope and paging helpers | none |
| API | `api` | The full HTTP stack with the real security filter chain: auth, roles, CRUD, validation, versioning, idempotency, caching, error format, trace id, timeouts, OpenAPI | one shared context |
| Architecture | `architecture` | Layering, constructor injection, DTOs are records, controllers never expose domain objects | none |

Conventions
- `*Test` classes are plain unit/architecture tests; `*ApiTest` classes extend `support.AbstractApiIntegrationTest`.
- Test data is created per test with unique SKUs; tests never delete or rely on state left by other tests (seeded products 1-30 are read-only for tests).
- `src/test/resources/application-test.yml` (profile `test`): fixed JWT secret, no simulated latency.
- Test names use underscores and are rendered as sentences (`junit-platform.properties`).

Run
    ./gradlew test                                  # everything (CI does this)
    ./gradlew test --tests "*unit*"                 # fast unit tests only
    ./gradlew test --tests "*ApiTest"               # API tests only
    ./gradlew test --tests "*ArchitectureTest"
Reports: build/reports/tests/test/index.html
