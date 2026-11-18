# Black-box API tests

These tests know nothing about the Java code. They call a **running** application over HTTP, exactly as a client would,
so they can run against local, CI, staging or (smoke only) production. Built as Postman collections and run with
[Newman](https://github.com/postmanlabs/newman) (needs Node.js; `npx` fetches Newman on demand).

| Suite | Requests | Purpose | Writes data? | Where it may run |
|---|---|---|---|---|
| `smoke` | 6 | Broad and shallow: alive, can log in, auth enforced, can read, docs served. Fails fast. | **No (read-only)** | everywhere, including production, after every deployment |
| `sanity` | 14 | Focused: the core product flows (CRUD lifecycle, retry safety, roles, versions) work end to end | yes, cleans up after itself | local, CI, test, staging |
| `regression` | 72 | Everything: every endpoint, role, validation rule, error, version, header, status code and timeout | yes, cleans up after itself | local, CI, test, staging (never production) |

## Run
```bash
./gradlew bootRun                              # terminal 1: start the application
./run-newman.sh smoke                          # terminal 2 (Windows: run-newman.bat smoke)
./run-newman.sh sanity
./run-newman.sh regression
BASE_URL=https://staging.example.com ./run-newman.sh smoke staging
```
JUnit XML reports are written to `reports/` (git-ignored) for CI dashboards.

## Notes
- The first requests of `sanity` and `regression` log in and store the tokens in collection variables.
- Seeded products 1-30 are treated as read-only; every write test creates and removes its own product.
- `regression` includes a request that takes about 3 seconds on purpose (upstream timeout, expected 504).
- You can also import any collection file into Postman and run it with the Collection Runner.
