# ADR-0003: MySQL everywhere — drop H2

- **Status**: accepted
- **Date**: 2026-09-25
- **Supersedes**: the H2 parts of ADR-0001 (decision 1, "Database", and the
  related consequences)

## Context

ADR-0001 chose MySQL for real environments and in-memory H2 for local runs
and tests, with Flyway migrations mirrored in `db/migration/{h2,mysql}`.

Building chunk 0 against both showed the cost straight away:

- Two copies of every migration in different dialects (`UUID` vs
  `BINARY(16)`, `BOOLEAN` vs `BIT(1)`, `TIMESTAMP` vs `DATETIME(6)`).
- Config bugs that H2 hid: the `mysql` profile inherited H2's pinned JDBC
  driver, and `characterEncoding=utf8mb4` is rejected by MySQL Connector/J
  (it needs `UTF-8`). Both only appeared when the suite ran on real MySQL.
- The features ahead rely on MySQL-specific behaviour (`JSON` columns,
  `FULLTEXT` search) that H2 doesn't reproduce.

The team asked to use MySQL.

## Decision

- **MySQL 8 is the only database**: local dev, automated tests, CI and
  production. H2 is removed from the build.
- **One migration folder**: `backend/src/main/resources/db/migration`.
- **Local dev**: a local MySQL (`ats` database, user `ats`/`ats` by default;
  overridable with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). The `dev` profile
  adds only fake seed users and the dev login — not a different database.
- **CI**: GitHub Actions runs the backend tests against a MySQL 8 service
  container.
- **Tests** run against a real MySQL database that may outlive a run, so tests
  must not depend on an empty database (e.g. use unique emails).

## Consequences

- Developers need MySQL running locally (native install or
  `docker run mysql:8`) to run the app or the tests.
- Every migration is written once, for MySQL, and is verified by the normal
  test run.
- Tests are slightly slower than in-memory H2; acceptable for this project's
  size.
