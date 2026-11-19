# Test Plan: Spring Boot REST API Blueprint

## 1. Purpose and scope
Give fast, trustworthy evidence that the API behaves as documented: CRUD, validation, error handling (including timeouts),
header versioning, JWT authentication and authorization, idempotency, caching and the published OpenAPI contract.

In scope: everything the API does over HTTP and the rules inside the code that implement it.
Out of scope (known gaps, see section 10): real database behaviour (storage is in-memory), multi-instance behaviour
(cache and idempotency store are per instance), browser/UI, third-party systems.

## 2. Test levels

| Level | Location | Count | What it proves | Spring context | Tag |
|---|---|---|---|---|---|
| Unit | `unit/` | 65 | Business rules in isolation: paging/sorting/filtering, create/replace/patch/delete, login logic, JWT claims and tamper resistance, idempotency replay and concurrency, repository thread safety, the status + envelope for every exception type | none | `unit` |
| Architecture | `architecture/` | 7 | Layering, constructor injection, DTOs are records, controllers never expose domain objects | none | `architecture` |
| Integration | `integration/` | 116 | The real HTTP stack with the real security filter chain. `auth/`, `product/`, `platform/` (errors, timeouts, OpenAPI), `controller/` (web layer with mocked services) | one shared context (+1 for mocks) | `integration` |
| Contract | `contract/` | 20 | Exact response shapes, one error envelope for all errors, status code matrix per role, headers, no removal of operations/responses/properties versus the OpenAPI baseline | shared | `contract` |
| Smoke (in-process) | `suites/SmokeSuiteTest` + context test | 7 | Alive and main doors open | shared | `smoke` |
| Sanity (in-process) | `suites/SanitySuiteTest` | 4 | Core product flows end to end | shared | `sanity` |
| Regression pins | `regression/` | 8 | Past defects and easy-to-break behaviour stay fixed | shared | `regression` |
| Black-box API | `api-tests/` | 92 requests (6 smoke, 14 sanity, 72 regression) | The deployed system over real HTTP, any environment | n/a (separate process) | n/a |
| Performance | `jmeter/` | scenario plan | Throughput and latency, cached vs uncached | n/a | n/a |

In-process total: 227 test methods (parameterized tests run several times each).

## 3. Suites

| Suite | Size / target time | Contains | Gradle task | Black-box counterpart |
|---|---|---|---|---|
| Smoke | 7 tests, seconds | health, login, auth enforced, read, create, docs served | `smokeTest` | `api-tests/smoke` (read-only) |
| Sanity | 4 tests, under a minute | CRUD lifecycle, retry safety, roles, versions | `sanityTest` | `api-tests/sanity` |
| Regression | all of the above, minutes | every in-process test | `regressionTest` | `api-tests/regression` |
| Unit | 65 tests, seconds | | `unitTest` | |
| Integration | 116 tests, about a minute | | `integrationTest` | |
| Contract | 20 tests | | `contractTest` | |
| Architecture | 7 tests | | `architectureTest` | |
| Everything | | | `test` | |

Definitions used here: **smoke** = broad and shallow, is it alive; **sanity** = narrow and focused, does the changed area still
work end to end; **regression** = everything, did anything that used to work break.

## 4. When to run what

| Moment | Run | Why |
|---|---|---|
| While coding | the unit test of the class you edit | feedback in seconds |
| Before pushing | `unitTest architectureTest` | catches silly mistakes in about a minute |
| Every pull request / push | CI: unit + architecture, then integration + contract, then black-box smoke + sanity against the started app | automated regression gate on the code |
| After any deployment | black-box **smoke** (automatic) | broken release found in under a minute |
| After a hotfix or targeted change (test/staging) | **sanity** for that area | quick confidence without the full run |
| Before a release (staging) | **regression** (in-process + black-box), security scan, performance run compared to the last baseline | release gate |
| Nightly | in-process regression + black-box regression; weekly: mutation test, dependency scan, soak test | catches slow drift |
| After a production deploy | black-box **smoke only** (read-only) plus monitoring | never run write tests in production |

## 5. Environments and data policy

| Environment | Suites allowed | Data rule |
|---|---|---|
| Developer machine | everything | disposable in-memory data |
| CI | everything except performance | fresh app per run |
| Test / staging | smoke, sanity, regression | tests create and delete their own data; seeded products 1-30 are read-only for tests |
| Production | smoke only | read-only requests, no write tests |

Isolation rules: every test creates its own data with a unique SKU; tests never depend on each other or on state left behind;
the test profile (`application-test.yml`) fixes the JWT secret and removes simulated latency.

## 6. Entry and exit criteria
- **Merge to main:** unit, architecture, integration and contract suites green; black-box smoke and sanity green in CI.
- **Release:** regression (in-process and black-box) green; contract baseline reviewed; no open critical/high security findings;
  p95 latency not more than 10% worse than the stored JMeter baseline; error rate 0%.
- **Production deploy done:** smoke green, no error spike in monitoring for 15 minutes.
- A failing test is never ignored or deleted to get green: fix the code, fix the test (with a reason in the commit), or tag-skip with a ticket.

## 7. Traceability (requirement -> tests)

| Requirement | Main tests |
|---|---|
| GET, GET-all, POST, PUT, PATCH, DELETE | `integration/product/ProductReadApiTest`, `ProductWriteApiTest`, `unit/service/ProductServiceTest`, `suites/SanitySuiteTest` |
| Query parameters, path variables | `ProductReadApiTest` (paging, filter, sort, search, bad ids) |
| OpenAPI documentation | `integration/platform/OpenApiDocsTest`, `contract/OpenApiBaselineContractTest` |
| All response codes | `integration/platform/DemoEndpointsApiTest`, `contract/HttpSemanticsContractTest`, `contract/ErrorContractTest` |
| Exception handling incl. timeouts | `unit/exception/GlobalExceptionHandlerTest`, `integration/platform/ErrorHandlingApiTest`, `DemoEndpointsApiTest` (504), `api-tests/regression` folder 09 |
| Header versioning | `integration/product/VersioningApiTest`, `contract/ResponseSchemaContractTest` |
| Nested response classes | `unit/dto/ApiResultTest`, `contract/ResponseSchemaContractTest`, `contract/ErrorContractTest` |
| Authentication and authorization | `integration/auth/*`, `unit/security/JwtServiceTest`, `unit/service/AuthServiceTest`, `contract/HttpSemanticsContractTest` |
| Idempotent APIs | `integration/product/IdempotencyApiTest`, `unit/idempotency/IdempotencyServiceTest`, PUT/DELETE idempotency in `ProductWriteApiTest` |
| In-memory caching | `integration/product/CachingApiTest` |
| Thread safety | `unit/repository/ProductRepositoryTest`, concurrency tests in idempotency unit and API tests |
| Design rules | `architecture/ArchitectureTest` |
| Controller wiring and "security before business code" | `integration/controller/ProductControllerSliceTest` |
| Performance | `jmeter/api-load-test.jmx` |

## 8. Conventions
- Test names are sentences with underscores (`junit-platform.properties` renders them readably).
- Naming: `*Test` for in-process classes, `*ApiTest` for classes extending `AbstractApiIntegrationTest`.
- Every top-level test class carries exactly one suite annotation from `support/` (`@UnitTest`, `@IntegrationTest`, `@ContractTest`,
  `@ArchitectureCheck`, `@SmokeTest`, `@SanityTest`, `@RegressionTest`). Tags drive the Gradle tasks; untagged tests would be skipped by suite tasks.
- Test data: `support/TestData` (builder, unique SKUs) and `resources/test-data/*.json` fixtures.
- **Bug-fix rule:** fix a defect, then add a test named `issue_...` to `regression/KnownIssuesRegressionTest` first or in the same commit.
- **Contract change rule:** a deliberate change to a response shape updates the contract test and bumps the API version.

## 9. Reporting
Gradle HTML report `build/reports/tests/<task>/index.html`; JUnit XML for CI; Newman JUnit XML in `api-tests/reports/`;
CI uploads reports and the application log as artifacts on every run.

## 10. Known gaps and planned improvements
- Repository is in-memory: add repository tests with Testcontainers when a real database is introduced.
- No coverage or mutation gate yet (JaCoCo, PIT planned).
- No automated security scan or API fuzzing in CI (OWASP ZAP, Schemathesis planned).
- Cache and idempotency store are per instance; behaviour with several instances is untested.
- The OpenAPI baseline file must be created once (see `OpenApiBaselineContractTest`); until then that test is skipped.
- Black-box tests are not yet run in a dedicated staging environment.
