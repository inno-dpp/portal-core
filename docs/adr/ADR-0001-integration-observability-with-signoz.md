# ADR-0001: Integration Observability with SigNoz

## Status
Proposed

## Context
The portal orchestrates CKAN/SPIP adapters, ingestion jobs, and scheduled integrations whose failures are often reported only through sparse logs and manual checks. Operators currently lack end-to-end visibility across connectors, queues, and downstream services, making it difficult to diagnose latency spikes, SLA breaches, or data mismatches. We need a consistent observability layer that captures traces, metrics, and structured logs across integration code paths without introducing a SaaS dependency that conflicts with privacy requirements for partner datasets.

## Decision
Adopt SigNoz as the observability backend for integration components. Instrument Spring Boot services, integration pipelines, and adapter clients with OpenTelemetry SDKs/exporters, emitting traces, service metrics (latency, throughput, error rates), and structured logs to the self-hosted SigNoz cluster that is deployed alongside the existing Docker stack. Define required signal retention (14 days for traces, 30 days for metrics), baseline dashboards, and alert rules for connector error rates so integration teams can monitor health in real time.

## Alternatives Considered
- Rely solely on existing application logs and ad-hoc Kibana dashboards: rejected because they do not provide distributed tracing, require manual correlation across services, and have historically failed to surface integration regressions quickly.
- Use the Elastic/ELK stack for metrics and traces: rejected due to higher infrastructure cost, heavier operational footprint, and limited in-house expertise maintaining Elastic clusters.
- Adopt a hosted APM solution (Datadog/New Relic): rejected to avoid externalizing potentially sensitive operational metadata and to remain within the project budget constraints.

## Consequences
- Positive: Provides unified visibility (traces/metrics/logs) for integration flows, shortens mean time to detection, and enables proactive alerting on adapter failures. OpenTelemetry standardization reduces vendor lock-in and keeps instrumentation portable.
- Neutral/Negative: Requires ongoing maintenance of the SigNoz deployment (storage sizing, upgrades) and coordination with DevOps to ensure collectors run in each environment. Instrumentation adds minor runtime overhead and necessitates developer training to emit meaningful spans and attributes.

## References
- https://signoz.io/docs/
- https://opentelemetry.io/docs/
