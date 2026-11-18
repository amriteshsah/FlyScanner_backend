# Sabre Flight Shop Lite — Sandbox setup

This integration is currently implemented on the `feature/sabre-flightshop-lite` branch.

## What it adds

- Sabre v2 OAuth token service (token cached in process with a 60-second expiry safety window)
- Flight Shop Lite adapter implementing the existing `FlightSupplierAdapter` interface
- Mapping between FlyScanner's canonical search request and Sabre's Lingo request
- Normalization of Sabre flights, journeys, offers and total price into FlyScanner domain objects
- Provider-specific offer reference preserved as `offerToken`
- Sabre profile disables mock supplier by default
- Configurable offer limit and optional PCC

## Sabre access you need

From Sabre Developer Hub / your account manager, obtain and confirm:

- CERT/Sandbox EPR username
- EPR group
- EPR domain
- Password
- PCC, if required for your shopping configuration
- Flight Shop Lite entitlement/content availability

Do not paste credentials into chat, GitHub, or source code.

## Local setup

Use JDK 21 and Maven, as required by this repository.

### PowerShell

Set these in the terminal session only (replace values with your own):

```powershell
$env:SABRE_USERNAME = "YOUR_EPR_USERNAME"
$env:SABRE_GROUP = "YOUR_EPR_GROUP"
$env:SABRE_DOMAIN = "YOUR_EPR_DOMAIN"
$env:SABRE_PASSWORD = "YOUR_EPR_PASSWORD"
$env:SABRE_PCC = "YOUR_PCC"
$env:SABRE_ENABLED = "true"
$env:SPRING_PROFILES_ACTIVE = "local,sabre"

mvn spring-boot:run
```

### Bash

```bash
export SABRE_USERNAME="YOUR_EPR_USERNAME"
export SABRE_GROUP="YOUR_EPR_GROUP"
export SABRE_DOMAIN="YOUR_EPR_DOMAIN"
export SABRE_PASSWORD="YOUR_EPR_PASSWORD"
export SABRE_PCC="YOUR_PCC"
export SABRE_ENABLED=true
export SPRING_PROFILES_ACTIVE=local,sabre

mvn spring-boot:run
```

The CERT endpoints are configured by default. Do not change to production URLs until your account is provisioned and the production integration is explicitly approved.

## Search request

The existing FlyScanner endpoint is retained:

```http
POST http://localhost:8080/api/v1/searches
Content-Type: application/json
```

Example:

```json
{
  "tripType": "ONE_WAY",
  "legs": [
    {
      "origin": "BLR",
      "destination": "DXB",
      "departureDate": "2027-01-15"
    }
  ],
  "passengers": {
    "adults": 1,
    "childrenAges": [],
    "infants": 0
  },
  "cabinClass": "ECONOMY",
  "currency": "INR",
  "market": "IN",
  "locale": "en-IN"
}
```

Sabre sandbox results are test content. They are not guaranteed to be bookable.

## Important limitations in this first integration

1. Airport-local flight times are currently represented in the existing FlyScanner `Instant` domain using a UTC placeholder. A proper airport timezone mapping is required before the UI can reliably display local times.
2. Children are mapped to the generic `CNN` passenger type; age-specific passenger-type rules need validation against the Sabre content and booking workflow.
3. Infants are mapped to `INF`; lap-infant association and any infant-with-seat use cases need a separate booking-phase validation.
4. This is shopping only. It does not perform Flight Check, booking, payment or ticketing.
5. Token caching is process-local. Distributed deployments should avoid multiple unnecessary token requests or move token coordination to a shared cache if required.
6. Search result caching is not included in this first connector commit; add it only after confirming provider terms and freshness rules.
7. A live Sandbox request and full Maven verification must be performed with your credentials before considering this integration validated.

## Verification

Run:

```bash
mvn clean verify
```

Then start the application with the Sabre profile and test the search endpoint. Confirm provider status is `SABRE`, inspect normalized offers, and verify that the mock provider is disabled in the Sabre profile.
