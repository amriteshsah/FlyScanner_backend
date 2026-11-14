# ADR 0001: Start with a modular monolith

- Status: Accepted
- Date: 2026-11-14

## Context

FlyScanner has an affiliate flight-search model and a long-term ambition to support substantial scale. Initial operating cost and delivery speed matter more than deploying many services before real traffic exists.

## Decision

Start with one Spring Boot deployable application and enforce business-module boundaries in code:

- account
- search
- affiliate
- alerts
- notifications

Each module owns its business rules and persistence. Modules must not call one another's repositories or access one another's tables directly. Inter-module collaboration uses application-facing interfaces and explicit events where asynchronous work is appropriate.

## Consequences

- One application image and deployment initially.
- Lower operational overhead and simpler local development.
- Horizontal scaling is possible because request handling is stateless.
- The search module can be extracted first when its independent scaling or release needs justify it.
- Extraction must preserve API contracts and data ownership.

## Explicitly deferred

Kubernetes, Kafka, Redis, service mesh, multiple databases, multi-region deployment and multiple always-on application services.
