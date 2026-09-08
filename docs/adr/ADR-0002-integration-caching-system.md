# ADR-0002: Integrate Caffeine Cache for CKAN Dataset Statistics

## Status
Accepted

## Context
The dashboard shows the total number of datasets hosted in the CKAN metadata platform. Every visit to `/` triggered a synchronous `getDatasetCount` call that authenticated against CKAN with a JWT and performed an API request. During demos and onboarding waves the dashboard is refreshed frequently, which amplified three pain points:

1. CKAN experienced avoidable load spikes and occasional throttling warnings.
2. Users perceived latency on first paint because the dashboard waited for CKAN before rendering.
3. Any short CKAN outage bubbled back as repeated log noise and failed dashboard widgets even though a slightly stale value would be acceptable.

We needed a lightweight caching layer that smooths CKAN calls without adding a new infrastructure dependency.

## Decision
Adopt Spring Cache backed by the in-memory Caffeine provider to cache CKAN dataset counts for one minute per application instance.

- `CacheConfig` enables caching globally (`@EnableCaching`) and configures a `CaffeineCacheManager` with the `datasetCount` cache, `maximumSize=100`, `expireAfterWrite=1m`, and statistics recording.
- `spring.cache` defaults in `application.yml` declare Caffeine as the cache type and mirror the TTL/size settings so profiles inherit the same behavior.
- `CkanStatisticsService#getDatasetCount` is annotated with `@Cacheable("datasetCount")`, so the first request populates the cache and subsequent dashboard loads reuse the cached value until it expires.

This decision keeps the footprint entirely inside the Spring Boot application and requires no additional services to run locally or in Docker.

## Alternatives Considered
1. **No caching (status quo)** – Simple but left CKAN subject to bursts, continued to block dashboard rendering on every request, and failed to degrade gracefully during CKAN hiccups.
2. **External cache (Redis/Memcached)** – Would provide cluster-wide coherence but adds operational burden (provisioning, monitoring, upgrades) and complicates local development; unjustified for a single integer refreshed every minute.
3. **Persist statistics in Postgres** – Another way to share state across instances, yet it requires new tables, background jobs to refresh counts, and adds write load to the primary database for minimal benefit.

## Consequences
**Positive**
- Reduces CKAN API calls by roughly the number of dashboard hits per minute, lowering latency and avoiding throttling.
- Keeps implementation self-contained and available in all profiles, improving developer parity.
- Cache statistics from Caffeine can be exposed later for observability if needed.

**Negative**
- Cache is per JVM; multiple portal instances may briefly display different counts until the TTL expires.
- Dataset count can be up to one minute stale, which is acceptable for the dashboard but should be documented.
- Future caching needs (e.g., per-organization metrics) may require expanding cache names or adopting a distributed cache if strong consistency is required.

## References
- `docs/adr/ADR-0001-integration-observability-with-signoz.md`
- `src/main/java/com/data4circ/portal/config/CacheConfig.java`
- `src/main/java/com/data4circ/portal/ckan/service/CkanStatisticsService.java`
- `src/main/resources/application.yml`
