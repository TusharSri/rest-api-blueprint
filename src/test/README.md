# Tests

Strategy, suite definitions, schedule and traceability: see **[TEST_PLAN.md](TEST_PLAN.md)**.

```
src/test/
|-- java/io/github/tusharsri/blueprint/
|   |-- unit/            service, repository, idempotency, security, exception, dto        (no Spring)
|   |-- architecture/    layering and design rules                                          (no Spring)
|   |-- integration/     auth/  product/  platform/  controller/                            (full HTTP stack, MockMvc)
|   |-- contract/        response shapes, error envelope, status matrix, OpenAPI baseline
|   |-- suites/          SmokeSuiteTest, SanitySuiteTest
|   |-- regression/      KnownIssuesRegressionTest (pinned defects)
|   `-- support/         base class, TestData, suite annotations
|-- resources/
|   |-- application-test.yml        test profile (fixed secret, no latency)
|   |-- junit-platform.properties   readable test names
|   |-- test-data/                  JSON fixtures
|   `-- contract/                   openapi-baseline.json (create once, see below)
|-- api-tests/        black-box Postman/Newman suites: smoke, sanity, regression (+ run scripts)
|-- gradle/           test-suites.gradle.kts  (suite tasks)
|-- ci/               GitHub Actions templates: ci, nightly-regression, post-deploy-smoke
|-- TEST_PLAN.md
`-- README.md
```

## Run
```bash
./gradlew test               # everything in-process (what CI gates on)
./gradlew unitTest           # seconds
./gradlew integrationTest
./gradlew contractTest
./gradlew architectureTest
./gradlew smokeTest          # is it alive?
./gradlew sanityTest         # do the core flows work?
./gradlew regressionTest     # all tagged suites

# black-box, against a running app (./gradlew bootRun in another terminal)
src/test/api-tests/run-newman.sh smoke        # Windows: src\test\api-tests\run-newman.bat smoke
src/test/api-tests/run-newman.sh sanity
src/test/api-tests/run-newman.sh regression
```

## Adding a test
- New behaviour: add an integration test in the matching `integration/` package (extend `AbstractApiIntegrationTest`) and, if it is pure logic, a unit test.
- New defect: add `issue_<what_broke>` to `regression/KnownIssuesRegressionTest`.
- Changed response shape: update `contract/ResponseSchemaContractTest` and bump the API version.
- Always annotate the top-level class with its suite annotation from `support/`.
