# FlyScanner Backend

Java 21 / Spring Boot backend foundation for FlyScanner.

The first release is a modular monolith: one deployable Spring Boot application with explicit business boundaries. This keeps the initial platform inexpensive while allowing the flight-search module to be extracted when measured demand justifies it.

## Current scope

- Spring Boot 4.1.2 and Java 21
- Spring MVC REST API and Actuator health probes
- H2 in-memory database for default local development
- PostgreSQL profile retained for compatibility testing and deployment
- Flyway migrations shared across database profiles
- Correlation IDs and Problem Details API errors
- Docker Compose for PostgreSQL parity testing
- GitHub Actions build and test workflow
- Initial account, search, affiliate, alert and notification module boundaries
- \`POST /api/v1/searches\` with request validation and a mock supplier adapter
- Canonical offer response, provider status and partial-failure model
- Bounded supplier-search executor with configurable provider timeout

No real flight supplier, production authentication provider, email vendor, affiliate partner or AWS resources are connected yet. Mock fares and schedules are synthetic and not bookable.

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop / Docker Engine with Compose plugin (only needed for PostgreSQL parity run)

## Run locally with H2 (default)

No PostgreSQL installation or Docker container is required for the normal Phase 1 local development workflow.

\`\`\`bash
mvn spring-boot:run
\`\`\`

Spring Boot uses the \`local\` profile by default and creates an in-memory H2 database. The database is recreated when the JVM starts.

Check health and API:

\`\`\`bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/health/readiness
curl http://localhost:8080/api/v1/system/info
\`\`\`

Run a one-way search (use a date in the future):

\`\`\`bash
curl -X POST http://localhost:8080/api/v1/searches \\
  -H "Content-Type: application/json" \\
  -d '{
    "tripType": "ONE_WAY",
    "legs": [
      { "origin": "BLR", "destination": "DXB", "departureDate": "2027-01-15" }
    ],
    "passengers": { "adults": 1, "childrenAges": [], "infants": 0 },
    "cabinClass": "ECONOMY",
    "currency": "INR",
    "market": "IN",
    "locale": "en-IN"
  }'
\`\`\`

## Run PostgreSQL compatibility check

The \`postgres\` Spring profile is retained and is used by Docker Compose. This lets us verify the same Flyway migrations and application against PostgreSQL before Phase 1 is closed.

\`\`\`bash
docker compose up --build
\`\`\`

Alternatively, start PostgreSQL only:

\`\`\`bash
docker compose up -d postgres
\`\`\`

Then run the app using PostgreSQL:

\`\`\`bash
SPRING_PROFILES_ACTIVE=postgres mvn spring-boot:run
\`\`\`

PowerShell:

\`\`\`powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
mvn spring-boot:run
\`\`\`

Default local PostgreSQL connection settings can be overridden using \`DB_URL\`, \`DB_USERNAME\` and \`DB_PASSWORD\`.

## Build and test

\`\`\`bash
mvn clean verify
\`\`\`

## Database profile rules

| Profile | Database | Intended use |
|---|---|---|
| \`local\` (default) | H2 in-memory, PostgreSQL compatibility mode | Fast local Phase 1 development |
| \`postgres\` | PostgreSQL | Migration compatibility checks and later deployment |

Important:

1. H2 is for local development only, not staging or production.
2. H2 in-memory contents disappear when the JVM stops.
3. Keep Flyway as the only schema migration mechanism.
4. Prefer portable SQL and avoid H2-only syntax.
5. PostgreSQL-specific features must be verified against the real PostgreSQL profile.
6. Before moving to production, run the full integration suite against the selected PostgreSQL version.
7. Do not copy H2 database files into PostgreSQL. Use versioned Flyway migrations and an explicit data export/import process if development data must be retained.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| \`SERVER_PORT\` | \`8080\` | HTTP port |
| \`SPRING_PROFILES_ACTIVE\` | \`local\` by default | Select \`local\` or \`postgres\` |
| \`DB_URL\` | local PostgreSQL URL in postgres profile | JDBC URL override |
| \`DB_USERNAME\` | \`flyscanner\` | PostgreSQL user |
| \`DB_PASSWORD\` | local-only value | PostgreSQL password |
| \`DB_POOL_MAX_SIZE\` | \`5\` | Hikari maximum pool |
| \`flyscanner.search.provider-timeout\` | \`3s\` | Per-provider response budget |
| \`flyscanner.search.executor.core-size\` | \`2\` | Supplier executor core threads |
| \`flyscanner.search.executor.max-size\` | \`8\` | Supplier executor maximum threads |
| \`flyscanner.search.executor.queue-capacity\` | \`32\` | Supplier executor queue limit |

Local Compose credentials are only for development. Never reuse them in shared environments.

## API documentation

See \`docs/architecture/flight-search-api.md\` for request rules, response status semantics and limitations.

## Architecture rules

1. Keep controllers thin; business behaviour belongs in application/domain code.
2. Keep the application stateless.
3. Each module owns its data and migrations.
4. Never accept arbitrary redirect URLs from the browser.
5. Apply independent supplier timeouts, concurrency and rate limits when real providers are added.
6. Keep provider credentials out of source control and frontend code.
7. Treat flight offers as transient supplier data, subject to freshness and storage terms.
8. Add infrastructure only when a measured requirement justifies its cost.
