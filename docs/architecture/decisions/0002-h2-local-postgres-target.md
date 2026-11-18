# ADR 0002: H2 for local Phase 1 development, PostgreSQL as target database

- Status: Accepted
- Date: 2026-11-14

## Context

Phase 1 is focused on completing application workflows with low local setup cost. PostgreSQL is the intended production relational database, but requiring every developer to start a database service for early development adds avoidable setup.

## Decision

- Default Spring profile is \`local\`, using H2 in-memory in PostgreSQL compatibility mode.
- Keep a separate \`postgres\` profile using the PostgreSQL JDBC driver.
- Keep Flyway as the single source of schema migrations.
- Keep PostgreSQL available through Docker Compose so the codebase can be checked against the target database during development.
- H2 must never be used for production or production-like performance testing.

## Compatibility rules

H2 PostgreSQL mode is a convenience, not a complete PostgreSQL emulator. Use portable SQL in shared migrations and test PostgreSQL-specific behaviour against PostgreSQL itself. Do not use H2-specific SQL, functions, data types or generated schema behaviour in application code.

## Phase 1 exit criteria

Before production migration:
1. Run all database integration tests against the chosen PostgreSQL major version.
2. Validate every Flyway migration against a clean PostgreSQL database.
3. Verify indexes, constraints, transaction isolation and query plans on PostgreSQL.
4. Define a repeatable data export/import plan if any non-disposable data exists.
5. Switch the deployment profile and secrets to PostgreSQL; do not copy H2 files as a database migration.

## Consequences

- Local application startup does not require Docker or PostgreSQL.
- PostgreSQL compatibility remains testable at any point.
- Developers must not treat a passing H2 test as proof of PostgreSQL compatibility.
