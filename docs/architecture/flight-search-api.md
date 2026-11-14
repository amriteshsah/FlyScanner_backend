# Flight search API v1

## Endpoint

POST /api/v1/searches

The first implementation uses a deterministic mock supplier. It returns synthetic, non-bookable sample offers for frontend integration and contract testing.

## Example request

    {
      "tripType": "ROUND_TRIP",
      "legs": [
        { "origin": "BLR", "destination": "DXB", "departureDate": "2027-01-15" },
        { "origin": "DXB", "destination": "BLR", "departureDate": "2027-01-22" }
      ],
      "passengers": {
        "adults": 2,
        "childrenAges": [6],
        "infants": 0
      },
      "cabinClass": "ECONOMY",
      "currency": "INR",
      "market": "IN",
      "locale": "en-IN"
    }

## Contract

- tripType is ONE_WAY or ROUND_TRIP.
- One-way requires exactly one leg; round-trip requires two reversed route legs.
- Airport values are uppercase three-letter IATA codes.
- Departure dates are today or later.
- Adults must be 1 to 9; infants cannot exceed adults.
- Total passengers cannot exceed 9.
- Currency is a three-letter uppercase code; market is a two-letter uppercase code.
- The mock supplier currently returns INR fixtures only. It does not convert currencies.

## Response semantics

- SUCCESS: offers returned and all providers completed without failure.
- PARTIAL_SUCCESS: at least one provider returned offers and another provider failed or timed out.
- NO_RESULTS: providers responded but returned no offers.
- UNAVAILABLE: every configured provider failed or timed out.

Provider statuses report SUCCESS, NO_RESULTS, TIMEOUT or ERROR independently.

Each offer includes an opaque offer token, provider, price, itinerary, checked time and expiry. The mock adapter marks affiliateRedirectAvailable as false. No booking or affiliate destination is connected.

## Limitations

- Mock schedules and prices are illustrative, not live or bookable.
- The mock price fixtures are only for INR; no exchange-rate conversion is performed.
- No real supplier credentials are configured.
- No offer revalidation or affiliate redirect endpoint exists yet.
- Supplier calls use a bounded executor and timeout budget. Real provider adapters must also configure transport-level connection/read timeouts and provider-specific rate limits.
