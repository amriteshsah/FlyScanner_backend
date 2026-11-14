# FlyScanner Backend

Java 21 / Spring Boot backend foundation for FlyScanner.

The first release is a modular monolith: one deployable Spring Boot application with explicit business boundaries. This keeps the initial platform inexpensive while allowing the flight-search module to be extracted when measured demand justifies it.

## Current scope

- Spring Boot 4.1.2 and Java 21
- Spring MVC REST API, Actuator health probes and Swagger UI / OpenAPI documentation
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

### Open the H2 database console

While the application is running with the local profile, open:

- URL: http://localhost:8080/h2-console
- JDBC URL: jdbc:h2:mem:flyscanner;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
- User name: sa
- Password: leave blank

The H2 console is enabled only in application-local.yml. Do not enable it in production or expose it outside your local machine.

### Open Swagger UI

Start the backend with the default `local` profile, then open:

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

Swagger UI includes the account, flight-search and price-alert endpoints. Use the **Authorize** control and enter the access token returned by registration or login to try protected profile and price-alert operations. The HTTP bearer scheme adds the `Bearer` prefix automatically.

The docs endpoints are enabled by default only in the `local` profile. In another profile they remain disabled unless `SWAGGER_ENABLED=true` is configured. Keep API documentation disabled in production unless there is an approved access-control policy for it.

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

## Verify persisted data in H2

Each successful call to `POST /api/v1/searches` now stores the search request and returned offer snapshot in the local H2 database within one transaction.

Core search tables:

| Table | Stored data |
|---|---|
| `search.flight_searches` | Search header, passengers, market, cabin and outcome |
| `search.flight_legs` | Outbound and return route legs |
| `search.search_children` | Child age values supplied with the search |
| `search.provider_search_statuses` | Supplier status, latency and message |
| `search.flight_offers` | Price, currency, provider, stops and expiry |
| `search.flight_journeys` | Journey details for each offer |
| `search.flight_segments` | Individual flight segments |

Additional module tables are created for future functionality: `account.users`, `affiliate.click_tracking`, `alerts.price_alerts` and `notifications.notification_history`. Those are not populated by the current search flow because login, affiliate redirect, price-alert and notification workflows are not implemented yet.

Use these queries in H2 Console:

```sql
-- List all application tables
SELECT TABLE_SCHEMA, TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA IN ('account', 'search', 'affiliate', 'alerts', 'notifications')
ORDER BY TABLE_SCHEMA, TABLE_NAME;

-- Recent searches
SELECT * FROM search.flight_searches
ORDER BY created_at DESC;

-- Offers returned for each search
SELECT search_id, offer_token, provider_code, price_amount, price_currency,
       stop_count, total_duration_minutes, checked_at, expires_at
FROM search.flight_offers
ORDER BY created_at DESC, price_amount;

-- Flight segments for an offer
SELECT * FROM search.flight_segments
WHERE offer_token = '<copy an offer_token from search.flight_offers>'
ORDER BY leg_number, segment_number;

-- Provider outcome for each search
SELECT * FROM search.provider_search_statuses
ORDER BY search_id;
```

The local H2 database is in-memory. Data is cleared when the backend JVM stops. Search persistence is implemented for the current mock supplier; before retaining real supplier responses, confirm the supplier's data-storage and retention terms.

## Account authentication and price alerts

The account and price-alert APIs are available in the current foundation branch.

### Register an account

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"traveler@example.com","fullName":"Test Traveler","password":"Use-A-Unique-Password-2026!"}'
```

Registration returns an opaque bearer access token. Passwords are stored as BCrypt hashes. Access tokens are generated from cryptographically secure random bytes; only SHA-256 token hashes are persisted in `account.access_token_sessions`. Tokens expire after 12 hours by default and can be revoked at logout.

### Login and current profile

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"traveler@example.com","password":"Use-A-Unique-Password-2026!"}'
```

Use the returned token on protected endpoints:

```bash
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer <access-token>"
```

### Create, list and deactivate price alerts

```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "origin":"BLR",
    "destination":"DXB",
    "tripType":"ONE_WAY",
    "departureDate":"2027-03-15",
    "returnDate":null,
    "targetPrice":8500.00,
    "currency":"INR",
    "market":"IN"
  }'

curl http://localhost:8080/api/v1/alerts \
  -H "Authorization: Bearer <access-token>"

curl -X PATCH http://localhost:8080/api/v1/alerts/<alert-id>/deactivate \
  -H "Authorization: Bearer <access-token>"
```

Alerts are private to the authenticated user. Listing and deactivation always scope by both alert ID and owner ID.

### Logout

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer <access-token>"
```

The token is revoked immediately. Use login to issue a new token.

### Security and current limitations

- Public endpoints: registration, login, flight search and health/system-info endpoints.
- Protected endpoints: current account profile, logout and all price-alert operations.
- Password hashing uses BCrypt. Never log or persist submitted passwords.
- Access tokens are opaque bearer tokens, expire after 12 hours and are stored only as SHA-256 hashes.
- H2 console remains local-development-only.
- Add production HTTPS, login/register rate limiting, email verification, password reset, account lockout/abuse controls, CORS allow-list configuration and a refresh-token/device-session policy before public launch.
- Price-alert records are persisted, but scheduled supplier checks and email/push notifications are not yet implemented. No automatic price monitoring is running yet.

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

Interactive endpoint documentation is available through Swagger UI when enabled (see the local setup section above). The OpenAPI definition is generated from the Spring MVC controllers and request/response models.

See `docs/architecture/flight-search-api.md` for flight-search request rules, response status semantics and limitations.

## Architecture rules

1. Keep controllers thin; business behaviour belongs in application/domain code.
2. Keep the application stateless.
3. Each module owns its data and migrations.
4. Never accept arbitrary redirect URLs from the browser.
5. Apply independent supplier timeouts, concurrency and rate limits when real providers are added.
6. Keep provider credentials out of source control and frontend code.
7. Treat flight offers as transient supplier data, subject to freshness and storage terms.
8. Add infrastructure only when a measured requirement justifies its cost.
