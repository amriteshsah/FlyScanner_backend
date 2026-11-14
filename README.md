# FlyScanner Backend

Java 21 / Spring Boot backend foundation for FlyScanner.

The initial delivery is a **modular monolith**: one deployable Spring Boot application with explicit business boundaries. This keeps the first release inexpensive while making flight search a candidate for independent extraction when measured demand warrants it.

## Current scope

- Spring Boot 4.1.2 and Java 21
- Spring MVC REST API and Actuator health probes
- PostgreSQL with Flyway migrations
- Correlation IDs and consistent Problem Details error responses
- Docker Compose for local PostgreSQL + backend
- GitHub Actions build and test workflow
- Module boundaries: account, search, affiliate, alerts and notifications

No flight supplier, authentication provider, email vendor or AWS resources are connected yet. This foundation does not implement live flight search or real affiliate tracking.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop / Docker Engine with Compose plugin

## Run locally with Docker

\`\`\`bash
docker compose up --build
\`\`\`

Check the API:

\`\`\`bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/v1/system/info
\`\`\`

Stop containers while preserving local data:

\`\`\`bash
docker compose down
\`\`\`

Delete the local database volume only when you intentionally want to remove local data:

\`\`\`bash
docker compose down -v
\`\`\`

## Run with Maven

Start PostgreSQL first:

\`\`\`bash
docker compose up -d postgres
mvn spring-boot:run
\`\`\`

Build and test:

\`\`\`bash
mvn clean verify
\`\`\`

## Local database credentials

Credentials in Docker Compose and local application defaults are only for local development. Never reuse them in shared environments. Use injected secrets and environment-specific database users in AWS.

| Variable | Default | Purpose |
|---|---|---|
| SERVER_PORT | 8080 | HTTP port |
| DB_URL | jdbc:postgresql://localhost:5432/flyscanner | JDBC URL |
| DB_USERNAME | flyscanner | Local DB user |
| DB_PASSWORD | local-only value | Local DB password |
| DB_POOL_MAX_SIZE | 5 | Hikari maximum pool |
| DB_POOL_MIN_IDLE | 1 | Hikari minimum idle |

## Architecture rules

1. Keep controllers thin; business behaviour belongs in application/domain code.
2. Keep the application stateless; do not keep user sessions or essential state only in process memory.
3. Each module owns its tables, repositories and migrations.
4. Never accept arbitrary redirect URLs from the browser.
5. Apply independent supplier timeouts and concurrency limits when provider adapters are added.
6. Keep provider credentials out of source control and frontend code.
7. Treat flight offers as transient supplier data, subject to freshness and storage terms.
8. Add infrastructure only when a measured requirement justifies its cost.

See \`docs/architecture/decisions/\` for accepted architectural decisions.

## Next milestones

1. Add canonical flight-search request and offer contracts with mock supplier fixtures.
2. Add supplier capability and policy configuration.
3. Add click-time affiliate destination resolution and safe redirect handling.
4. Add account persistence and managed identity integration.
5. Add price-alert workers only after supplier terms allow scheduled searches.
6. Deploy to AWS using the separate \`FlyScanner_infrastructure\` repository.
