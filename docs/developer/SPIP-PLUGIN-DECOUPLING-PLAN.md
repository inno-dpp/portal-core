# SPIP Plugin Decoupling — Implementation Plan

**Status:** Milestones 1 and 2 complete. The repo is a Maven multi-module reactor
(`core` + `spip-plugin`); `mvn -pl core -am package` produces a jar with zero SPIP
implementation classes, verified by booting it standalone. One open item: whether/how
to keep shipping a full (core + SPIP) bundled Docker image before Milestone 3's proper
plugin-jar deployment mechanism exists — see the note at the end of Milestone 2. This
document exists so a fresh session (human or Claude) picked up in a sandbox clone has
full context without re-deriving it.

## Business goal

The portal is going open source. SPIP is a licensed, chargeable capability and must not
ship in the OSS core. Paying customers should get SPIP by adding one plugin — not by
forking or rebuilding from source. End state:

- Portal runs fully functional **without** SPIP (open-source core).
- Portal runs exactly as it does **today**, with SPIP, when the plugin is present.

## Current-state findings (as of 2026-09-07)

- 54 files reference SPIP by name across the codebase.
- `ONBOARDING_TOOL_SPIP_ENABLED` / `ONBOARDING_TOOL_SPIP_REQUIRED` only gate the
  onboarding wizard step. Everything else is unconditional today:
  - `SpipController` / `SpipSyncController` — plain `@Controller` beans, always mounted.
  - `SecurityConfig` permits `/spip/**` for `PLATFORM_ADMIN`, `ORG_ADMIN`,
    `SPIP_PRIVILEGED_USER` unconditionally (and that role lives in the core `UserRole`
    enum, not behind any toggle).
  - `fragments/navigation.html`'s SPIP nav item is role-gated, not module-gated.
  - `CkanOnboardingToolProvisioner` and `KeycloakOnboardingToolProvisioner` import
    `SpipOnboardingToolProvisioner.TOOL_KEY` directly — a compile-time dependency on
    SPIP's own source file.
  - `OrganizationSpipUser` is the credential table every tool ends up in (CKAN's
    `ckan_user`/`ckan_password` columns, added this week, are bolted onto a table
    named after SPIP).
- One genuine seam already exists: `SpipFacade` / `DefaultSpipFacade` — proof the
  codebase already reaches for an interface-based seam when it needs one, just not
  consistently.
- `ONBOARDING_TOOL_CKAN_*` decoupling from SPIP (independent CKAN credentials when
  SPIP is skipped) already shipped — see `CkanOnboardingToolProvisioner`,
  `CkanOnboardingSyncService.resolveCkanUsername/resolveCkanPassword`,
  `OrganizationSpipUser.ckanUser/ckanPassword`. That work is the template for how the
  rest of this decoupling should look (capability lookup, graceful fallback,
  conditional dependency).

## Guiding principles

1. **Absence must be structural, not conditional.** When SPIP is off, its beans must
   not exist in the Spring context — not exist-but-be-skipped by an `if`.
2. **Dependents name a capability, not a tool.** CKAN/Keycloak need "something that
   already produced identity credentials," not literally "SPIP."
3. **The extension point is the only public thing.** Capability interfaces live in the
   OSS core; SPIP's own implementation code never does, not even disabled.

## Milestones

### Milestone 1 — Capability interface + structural conditional loading
*(single repo, feature branch, normal PR flow)*

1. ✅ **Done.** Define an identity-provisioning capability (e.g. a marker interface or a
   `providesIdentity()` method on `OnboardingToolProvisioner`) that
   `SpipOnboardingToolProvisioner` implements.
2. ✅ **Done.** Update `CkanOnboardingToolProvisioner` / `KeycloakOnboardingToolProvisioner`
   to ask `OnboardingToolRegistry` for "any enabled tool providing identity" instead of
   importing `SpipOnboardingToolProvisioner.TOOL_KEY`. Removes the last compile-time
   reference between them.
   - Wiring note: `OnboardingToolRegistry.validateAndOrder()` (its own `@PostConstruct`)
     calls every tool's `getDependencies()` *while the registry itself is still being
     constructed*, so a dependent can't take the `OnboardingToolRegistry` bean directly
     (even `@Lazy`) without a `BeanCurrentlyInCreationException`. Fix: CKAN/Keycloak take
     a `@Lazy List<OnboardingToolProvisioner>` instead (Spring self-excludes the
     requesting bean from a same-typed collection injection, and `@Lazy` avoids a
     CKAN↔Keycloak construction cycle since both ask the same question) and call the
     registry's new **static** `OnboardingToolRegistry.findIdentityProvider(...)` helper
     against that list — no bean resolution involved. The registry's instance method
     `identityProvider()` delegates to the same static logic for use after startup.
3. ✅ **Done.** Introduce `app.modules.spip.enabled` (default `true`, so existing
   deployments are unaffected) and wrap every SPIP bean in `@ConditionalOnProperty`:
   `SpipController`, `SpipSyncController`, `SpipConfig` (the `spipRestTemplate` bean —
   see note), `SpipApiClient`, `SpipOnboardingToolProvisioner`, `DefaultSpipFacade`, plus
   `SpipOnboardingService`, `SpipSyncService`, `SpipLocalDataService` (not in the
   original list, but each is the sole reason a *different* listed bean would otherwise
   still fail to autowire `SpipApiClient` when disabled — Spring doesn't care that
   nothing calls them, only that the graph is constructable).
   - `SpipConfig.SpipProperties` (the nested `@ConfigurationProperties` class) was
     deliberately left **unconditional**: it's pure config data with no live behavior,
     and `SpipSettingsService` — used by the always-present `/admin/settings` page
     alongside CKAN and Keycloak — needs it regardless of module state. A nested
     `@Component` is discovered by classpath scanning independently of its enclosing
     `@Configuration` class's own `@Conditional`, so this required no special handling,
     just leaving the annotation off.
4. ✅ **Done** (folded into step 3 — the two aren't separably testable: step 3 alone
   left the app unable to boot with the property off). Audited every direct
   `SpipFacade`/`SpipApiClient`/`SpipLocalDataService` autowiring outside the onboarding
   registry and outside SPIP's own packages; switched to `Optional<T>` with graceful
   degradation: `OrganizationController` (2 SPIP-only endpoints, plus
   `@Value("${app.spip.customer-tag}")` → `:` default), `PlatformSettingsController`
   (`/spip/test`), `DataInitializer` (skips mock SPIP seeding), `CollaborationService`
   and `CollaborationCancellationSideEffects` (skip partner-attribute sync/teardown).
   `OnboardingRequestService`'s `SpipOnboardingToolProvisioner.TOOL_KEY` references are a
   remaining compile-time reference to SPIP's class from core, but it's a `String`
   constant, not a bean dependency — doesn't block boot-with-SPIP-off; still worth
   revisiting for Milestone 2/3's "zero references outside SPIP's package" bar.
   - Verified: `mvn test` (SPIP enabled, default) — full suite green. `mvn test
     -Dapp.modules.spip.enabled=false` — app boots and runs with **zero** Spring context
     failures; the only failures are 9 pre-existing test cases across 3 test classes that
     hardcode synchronizing the `"spip"` tool and correctly now get
     `IllegalArgumentException: Unknown or disabled onboarding tool: spip` — expected,
     not a regression (those tests assume SPIP is present and weren't written to run
     against a SPIP-off configuration).
5. ✅ **Done.** Make the nav item and the `/spip/**` security rule pluggable: a
   `List<NavContribution>`-style bean the nav fragment renders generically, and a
   security customizer SPIP's module registers instead of a hard-coded line in
   `SecurityConfig`.
   - `common/nav/NavContribution.java` (new, core): a record — `activePageKey`, `href`,
     `label`, `iconClass`, `requiredRoles`, `order`. `common/security/
     SecurityRuleContributor.java` (new, core): a `@FunctionalInterface` taking the
     `AuthorizeHttpRequestsConfigurer...AuthorizationManagerRequestMatcherRegistry` so a
     module can add its own `.requestMatchers(...)` rule.
   - `features/spip/config/SpipModuleUiConfig.java` (new, SPIP-owned, conditional on
     `app.modules.spip.enabled` like every other SPIP bean) registers both: the SPIP nav
     item and the `/spip/**` rule. `SecurityConfig` now takes `List<SecurityRuleContributor>`
     and applies each, in bean order, right before the `.anyRequest().authenticated()`
     catch-all — no `Spip`-named line left in `SecurityConfig` itself.
     `fragments/navigation.html`'s hard-coded SPIP `<li>` became a generic
     `th:each="navItem : ${navContributions}"`; `GlobalModelAttributesAdvice` exposes
     `${navContributions}` pre-filtered to the current user's `UserRole` and sorted by
     `order` (role-check happens server-side in Java, not via a client-visible
     `sec:authorize` string built from module data).
   - Pitfall hit and fixed: `@Autowired private List<NavContribution> navContributions`
     (field injection, implicitly `required=true`) throws `NoSuchBeanDefinitionException`
     when the list would be empty — a required `List<T>` autowiring still demands ≥1
     matching bean, and SPIP is currently the *only* contributor, so disabling it zeroes
     the list. Fixed with `@Autowired(required = false)` plus an inline `List.of()`
     default. (`SecurityConfig`'s *constructor* parameter of the same shape,
     `List<SecurityRuleContributor>`, did not need the same fix — Spring's constructor
     injection tolerates an empty collection there without extra annotation.)
   - Verified: `mvn test` (SPIP enabled, default) — full suite green. `mvn test
     -Dapp.modules.spip.enabled=false` — identical result to step 3/4's baseline (zero
     Spring context failures; same pre-existing 9 SPIP-flow test cases fail for the
     same expected reason) — step 5 introduced no new failures.
6. ✅ **Done, functionally** (one gap found — see below). Verify: boot with the property
   off, run the full suite. CKAN/Keycloak fully functional and independent; zero
   remaining direct references to SPIP's concrete classes outside its own package.
   - Added `src/test/java/.../integration/SpipModuleDisabledIntegrationTest.java`: boots
     the full app with `app.modules.spip.enabled=false` and — deliberately importing no
     SPIP class, so the test itself stays valid post-Milestone-2 — asserts (a) `"spip"`
     is absent from `OnboardingToolRegistry` and there's no identity provider, (b)
     `/spip` resolves to `NoResourceFoundException` (no controller bean), (c) CKAN and
     Keycloak synchronize and an approval completes end-to-end with **no** SPIP
     involvement and no phantom SPIP connector. Runs as part of the normal `mvn test`
     (SPIP enabled elsewhere; this one test class flips the property for its own
     context).
   - **Bug found and fixed during this verification:** disabling the module made
     `OnboardingToolConnectorService#materializeConfigOnlyConnectors` and
     `OnboardingController#buildToolViews` treat `"spip"` as if it were a genuinely
     config-only tool (like EDC) — no provisioner bean, but
     `app.onboarding.tools.spip.enabled` still defaulting `true` — and create/show a
     phantom, never-synchronized "SPIP Platform" connector for every approved
     organization. Fixed by cascading `application.yml`'s `tools.spip.enabled` and
     `tools.spip.connector.enabled` defaults from `app.modules.spip.enabled` via a
     nested placeholder: `${ONBOARDING_TOOL_SPIP_ENABLED:${app.modules.spip.enabled:true}}`
     — an explicit `ONBOARDING_TOOL_SPIP_ENABLED`/`..._CONNECTOR_ENABLED` still overrides.
     (First attempt nested `${SPIP_MODULE_ENABLED:...}` — the *env var*, not the
     resolved property — which silently didn't pick up a property-level override like a
     test's `@SpringBootTest(properties=...)`; fixed to nest `${app.modules.spip.enabled:...}`,
     the property itself.)
   - Verified: `mvn test` (SPIP enabled, default) — full suite green, including the new
     test class. `mvn test -Dapp.modules.spip.enabled=false` — zero Spring context
     failures; same pre-existing 9 SPIP-flow test cases across the same 3 test classes
     fail for the same expected reason (hardcode synchronizing `"spip"`, never written
     to run SPIP-off) — no new failures from this step.
   - **Audit: direct references to SPIP's concrete classes outside its own package.**
     Two categories found, neither addressed by this milestone (out of the 6 listed
     steps' scope) but both worth carrying forward:
     - **`OrganizationSpipUser`** (entity) and `OrganizationSpipUserRepository`, both
       under `features/spip/entity` / `features/spip/repository`: directly referenced by
       `Organization` (core entity — a `@OneToOne`/similar field), `CkanOnboardingSyncService`,
       `ConnectorApiController`, and `OrganizationSpipUserService`. This is the
       credential table CKAN's username/password columns were bolted onto (per the
       "Current-state findings" above) — it's a genuinely shared core concern living in
       SPIP's package, not SPIP-specific data. **This blocks Milestone 2's step 7 as
       written**: moving `features/spip/**` wholesale into `spip-plugin` would break
       `Organization` and CKAN's compile. Needs to move to a core package (e.g.
       `features/organization/entity`, possibly renamed) *before or as part of*
       Milestone 2's split — add this as an explicit new sub-step there, not a
       mechanical "just move the directory."
     - `OnboardingRequestService`'s two uses of `SpipOnboardingToolProvisioner.TOOL_KEY`
       — a `String` constant, not a bean dependency, so it doesn't affect boot-with-
       SPIP-off; still a compile-time `import` of SPIP's class from core that would break
       a Milestone 2 module split. Low-risk, mechanical fix when that split happens
       (replace with the literal `"spip"` or route through the registry).
     - Minor, no action needed: `features/organization/dto/SpipSyncResult.java` is a DTO
       misplaced in core's `dto` package but used exclusively by SPIP's own
       controller/service — trivial to relocate alongside the Milestone 2 move, not a
       real coupling (core doesn't depend on it). `common/util/SpipPolicyLabelUtil` and
       `features/platformsettings/service/SpipSettingsService` also reference SPIP by
       name but are legitimately shared/core (label-length validation `OnboardingRequestService`
       needs regardless of module state; connection settings for the always-present
       `/admin/settings` page) — not violations, left as designed in step 3.

### Milestone 2 — Mechanical module split, same repo

✅ **Done.** Both steps below landed together — they aren't separably provable, since
step 8's proof requires step 7 fully done.

0. **New prerequisite, found while starting this milestone** (not in the original
   6-item list): a real audit of "what does core still import from SPIP" turned up more
   than the one gap Milestone 1 flagged. Fixed before touching `pom.xml`:
   - **`OrganizationSpipUser` + `OrganizationSpipUserRepository`** moved from
     `features/spip/{entity,repository}` to `features/organization/{entity,repository}`
     (core). Class names and the `organization_spip_users` table/column names are
     unchanged — only the package moved, so no DB migration. ~12 files' imports fixed.
   - **`SpipFacade` was reachable from core** (`DataInitializer` called it directly to
     seed demo data), which would have dragged the interface — and its `SpipAttribute`/
     `SpipPolicy`/`SpipKeyStatus` return types — out of the plugin along with it,
     violating "the extension point is the only public thing." Fixed the same way as
     the nav/security seams in Milestone 1: a new core interface,
     `common/demo/DemoDataContributor`, that `DataInitializer` depends on generically
     (`List<DemoDataContributor>`); SPIP registers `SpipDemoDataContributor`
     (conditional, in its own package) implementing it. Each contributor is now
     responsible for its own per-organization idempotency (checked via
     `SpipAttributeRepository`), which is actually more correct than the old logic — the
     original global `spipAttributeRepository.count() == 0` backfill check would have
     under-seeded organizations 2-4 if reused per-org naively.
   - **Same problem, `CollaborationService`/`CollaborationCancellationSideEffects`**:
     both held `Optional<SpipLocalDataService>` directly. New interface
     `features/collaboration/spi/CollaborationPartnerHook`
     (`onPartnershipEstablished`/`onPartnershipEnded`, the latter returning failure
     notes for the audit trail); both core classes now depend on
     `List<CollaborationPartnerHook>`. SPIP registers `SpipCollaborationPartnerHook`.
     `CollaborationCancellationSideEffects` switched from field to constructor injection
     for this list in the process — constructor-injected `List<T>` tolerates zero beans
     without needing `required = false` (see Milestone 1's nav pitfall); field injection
     does not.
   - **`OrganizationController`/`PlatformSettingsController` held `Optional<SpipApiClient>`
     directly** for two genuinely SPIP-specific, non-generic features (no capability
     interface makes sense here — nothing else would implement "verify SPIP credentials
     and prefill a form from SPIP attributes," or "test the SPIP connection"). Extracted
     as their own controllers living in `features/spip/controller`
     (`SpipOrganizationBootstrapController`, `SpipConnectionTestController`), mapped at
     the *exact same URLs* the existing templates already call
     (`/organizations/new-from-spip`, `/organizations/validate-spip`,
     `/admin/settings/spip/test`) — so no template changes were needed. Trade-off
     accepted: the "Create from SPIP User" card on `organizations/list.html` isn't
     conditionally hidden when the module is absent (matches the pre-Milestone-1-nav-fix
     precedent for `/spip` itself) — clicking it 404s rather than disappearing. Flagged
     as a follow-up, not fixed now.
   - `OnboardingRequestService`'s `SpipOnboardingToolProvisioner.TOOL_KEY` references
     (flagged in Milestone 1's audit) replaced with a local `SPIP_TOOL_KEY = "spip"`
     constant.
   - `features/organization/dto/SpipSyncResult` (flagged in Milestone 1's audit) moved
     to `features/spip/dto`.
7. Root `pom.xml` became a multi-module aggregator: `core` and `spip-plugin`.
   `features/spip/**`, `integration/spip/**`, `onboardingsync/spip/**` moved into
   `spip-plugin` as planned. **`common/config/SpipConfig.java` needed to split, not just
   move**: its nested `SpipProperties` class is exactly the same kind of core-owned,
   side-effect-free config data as `OrganizationSpipUser` (`SpipSettingsService`, core,
   needs it) — moving the whole file would have broken core's build the same way
   `SpipFacade` almost did. `SpipProperties` is now its own top-level class in
   `core/.../common/config/`; `SpipConfig` (just the live `spipRestTemplate` bean) moved
   to `spip-plugin` and references core's `SpipProperties` by its new name.
   - Module layout: root `pom.xml` (artifactId `d4c-portal-parent`, `packaging=pom`,
     `<modules>core, spip-plugin</modules>`) keeps the `spring-boot-starter-parent`
     parent and the shared `<properties>`; `core/pom.xml` (artifactId `d4c-portal` —
     **unchanged**, so `core/target/d4c-portal-<version>.jar` is byte-for-byte the same
     filename as the old root-level jar, just relocated) carries the app's dependencies,
     `spring-boot-maven-plugin` and the SBOM plugin; `spip-plugin/pom.xml` (artifactId
     `d4c-portal-spip-plugin`, plain `packaging=jar`, not a bootable app on its own)
     depends on `core` and redeclares test-scope deps (Maven doesn't propagate a
     dependency's *test*-scope deps transitively).
   - Test placement: no dedicated test classes existed yet for SPIP's own service/
     controller layer (a pre-existing gap, not addressed here). Of the 8 test files that
     imported a SPIP class, 5 only needed `OrganizationSpipUser` and became core-clean
     automatically once it moved; the remaining 3 —
     `OnboardingOptionalKeycloakIntegrationTest`, `OnboardingOptionalToolIntegrationTest`,
     `OnboardingToolFlowIntegrationTest` — genuinely need both core and SPIP on the
     classpath (they `@MockBean SpipOnboardingService` to test the full onboarding flow)
     and moved to `spip-plugin/src/test/`. `SpipModuleDisabledIntegrationTest`
     (Milestone 1, step 6) needed no move — it was deliberately written to import zero
     SPIP classes, so it's still exactly where a "proves core works without SPIP" test
     belongs.
8. Verified:
   - `mvn clean test` at the root — full reactor green: 39 test classes in `core`, 3 in
     `spip-plugin` (the ones that moved), zero failures. This is the "full app runs
     exactly as before, with SPIP" proof — `spip-plugin`'s own test classpath includes
     core transitively, so its tests boot the complete application.
   - `mvn -pl core -am clean package` succeeds; `jar tf core/target/d4c-portal-*.jar |
     grep Spip` finds **only** the deliberate core exceptions —
     `SpipProperties`(+nested), `SpipPolicyLabelUtil`, `OrganizationSpipUser`,
     `OrganizationSpipUserRepository`, `OrganizationSpipUserService`,
     `SpipSettingsService` — and grepping for any real SPIP behavior
     (`SpipController|SpipApiClient|SpipFacade|SpipOnboarding|SpipSync|SpipLocalData|
     spip/entity/Spip[AKP]|templates/spip`) finds nothing. The plan's original step 8
     wording ("grep for Spip — must find nothing") was written before this audit found
     those core-owned exceptions exist for good reason (see item 0 above and
     Milestone 1 step 6) — the refined, actually-verified bar is "no SPIP *behavior*
     leaks into core," not "the substring never appears."
   - Booted `core/target/d4c-portal-*.jar` standalone (`spip-plugin` not on the
     classpath at all, not just disabled by property) with `--spring.profiles.active=dev`:
     full Spring context loads, `DataInitializer` logs "No demo-data contributors
     registered (e.g. SPIP module disabled) — nothing extra to seed" (proving
     `DemoDataContributor` degrades correctly with the module physically absent, not
     just property-disabled), Tomcat starts, `/login` renders, `/spip` behaves securely
     (redirects to login when unauthenticated, same as any other protected path with no
     handler).
   - Fixed fallout in tooling that assumed the old single-module layout, found by
     tracing what actually reads `pom.xml`/`target/`, not just source code:
     `Dockerfile` (build now copies both modules' `src/`, packages the whole reactor,
     copies the jar from `core/target/` instead of `target/`), `.github/workflows/ci.yml`
     (surefire-report paths made recursive — `**/target/surefire-reports/`),
     `.github/workflows/build-push.yml` (its version-bump step did a raw `sed` matching
     `<artifactId>d4c-portal</artifactId>` in root `pom.xml`, which no longer exists
     there — root's artifactId is now `d4c-portal-parent`, and child modules'
     `<parent><version>` also needed bumping, which sed never touched; replaced with
     `mvn versions:set`, matching the mechanism `release.config.js` already used),
     `release.config.js` (GitHub release asset path `target/*.jar` → `core/target/*.jar`;
     `@semantic-release/git` now commits all three `pom.xml` files `versions:set`
     touches, not just root's), `CLAUDE.md` (module structure, `-pl core` on
     `spring-boot:run` and single-class `-Dtest=` runs — confirmed by actually running
     one: `mvn test -Dtest=X` at the reactor root hard-fails with "No tests matching
     pattern" the moment it reaches a module that doesn't have that class, so single-test
     commands need `-pl core` or `-pl spip-plugin`).

**Open item, not resolved here:** `Dockerfile` now builds and ships **`core` alone** —
`spring-boot-maven-plugin`'s repackage only bundles a module's own declared
dependencies, and `spip-plugin` is not a dependency of `core` (only the reverse), so
there is no `mvn package` invocation today that produces one jar with both. Until
Milestone 3's real plugin-loading mechanism exists (`loader.path`/`PropertiesLauncher`,
per the distribution decisions below), any deployment relying on this repo's `Dockerfile`
to include SPIP will not have it. Two ways to restore that before Milestone 3, neither
implemented: (a) a third thin "distribution" module depending on both `core` and
`spip-plugin` with an explicit `<mainClass>` for the repackage goal, or (b) build core
and spip-plugin jars separately and lay them out on the classpath at container-start
time (a rough preview of Milestone 3's real mechanism). Needs a decision from whoever
owns the deployed environments before it matters in practice.

### Milestone 3 — Repo split (only after 1–2 are proven)

9. Publish core's capability interfaces as a versioned artifact.
10. Stand up `spip-plugin` as its own new private repo, seeded from the module carved
    out in Milestone 2.
11. Cut `portal-core` as the new public canonical repo — clean history, not a fork.
    This repo (`portal-for-circularity`) is superseded once that happens.

**Effort/risk note:** real technical risk lives in Milestones 1–2 (does conditional
loading actually behave, does the nav/security refactor hold, does the split produce a
genuinely clean core jar). Milestone 3 is comparatively mechanical once 1–2 work.

## Distribution / licensing decisions already made

- `spip-plugin` is a **separate private repo from day one**, not a monorepo module
  mirrored/filtered into the public repo. Makes leaking SPIP's source structurally
  impossible rather than dependent on a CI filter script nobody bypasses.
- Charging is enforced by **distribution, not code**: a non-paying customer simply
  never receives the `spip-plugin` jar. No license-key server needed unless a
  self-serve trial flow is wanted later.
- Deployment via Spring Boot's own `loader.path`/`PropertiesLauncher` (or
  `AutoConfiguration.imports` + `@ConditionalOnClass`) — a customer's install step is
  "place this jar in `/plugins`, restart," no core rebuild required. A dedicated
  plugin framework (PF4J) is not needed just to hit this bar.

## Open question

`git remote -v` in the original repo shows a second remote, `d4c` →
`github.com/inno-dpp/d4c-portal` (private) — confirmed to be the old repo name from
before a rename, not a pre-existing target for this work. `portal-core` /
`spip-plugin` in Milestone 3 are new repos, not a repurposing of `d4c-portal`.
