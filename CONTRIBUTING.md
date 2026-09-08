# Contributing to Portal for Circularity

Thank you for your interest in contributing! This document explains how to set up a development environment, the conventions we use, and how to get your changes merged.

## Code of Conduct

This project follows a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold it.

## Getting Started

### Prerequisites

- **Java 17** (JDK)
- **Maven 3.8+**
- **Docker** (optional, for PostgreSQL and containerized runs)

### Development Setup

1. Fork the repository and clone your fork:

   ```bash
   git clone https://github.com/<your-username>/portal-for-circularity.git
   cd portal-for-circularity
   ```

2. Run the application in development mode (H2 in-memory database, no external services required):

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   A default development `ENCRYPTION_KEY` is built into the dev profile (`application-dev.yml`), so no extra setup is needed. To use your own key, set the environment variable (exactly 32 characters):

   ```bash
   export ENCRYPTION_KEY=<your-32-character-key>
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. The portal is available at `http://localhost:8085`. The H2 console is at `http://localhost:8085/h2-console` (JDBC URL `jdbc:h2:mem:d4c_portal_dev`, user `sa`, empty password).

### Running Tests

```bash
mvn test
```

Notes:

- Tests use the `test` profile with an in-memory H2 database — no external infrastructure is needed for the standard test suite.
- `CkanIntegrationTest` is an end-to-end suite that requires a **live CKAN instance** and valid credentials. It is not expected to pass in a fork without that infrastructure; do not worry if you cannot run it locally.

## How to Contribute

### Reporting Bugs and Requesting Features

Please use the [issue templates](https://github.com/inno-dpp/portal-for-circularity/issues/new/choose). Before opening a new issue, search existing issues to avoid duplicates.

### Submitting Changes

1. Create a feature branch from `develop`:

   ```bash
   git checkout develop
   git checkout -b feat/my-feature
   ```

2. Make your changes. Keep commits atomic — one logical change per commit.

3. Ensure the build and tests pass:

   ```bash
   mvn clean verify
   ```

4. Push your branch and open a **Pull Request targeting `develop`** (not `master`). `master` holds stable releases; `develop` is the integration branch and publishes beta prereleases.

### Commit Message Convention

Releases and the changelog are generated automatically by semantic-release from [Conventional Commits](https://www.conventionalcommits.org/), so commit messages matter:

| Type | Example | Version bump |
|------|---------|--------------|
| New feature | `feat: add SPIP integration` | minor |
| Bug fix | `fix: resolve auth issue` | patch |
| Performance | `perf: optimize queries` | patch |
| Refactoring | `refactor: simplify code` | patch |
| Breaking change | `feat!: redesign API` + `BREAKING CHANGE:` footer | major |
| Documentation | `docs: update guide` | none |
| Tests | `test: add unit tests` | none |
| Maintenance | `chore: update deps` | none |

Use scopes where helpful, e.g. `feat(auth): add OAuth2`.

### Coding Guidelines

- Follow the existing package structure (`config/`, `controller/`, `dto/`, `entity/`, `repository/`, `service/`, `security/`, `integration/`) and layering (controller → service → repository).
- All new endpoints require authentication by default; use `@PreAuthorize` for role-based access control.
- Validate all user input in controllers; never expose sensitive configuration in templates.
- Add or update tests for the code you change.
- Match the style of the surrounding code; don't reformat unrelated files.
- **Never commit secrets** — credentials, tokens, API keys, or real infrastructure endpoints. Use environment variables and update `.env.example` with placeholder values if you add configuration.

### Pull Request Checklist

- [ ] PR targets the `develop` branch
- [ ] `mvn clean verify` passes locally
- [ ] Commits follow the Conventional Commits format
- [ ] Tests added/updated for the change
- [ ] Documentation updated where relevant
- [ ] No secrets or environment-specific values committed

## License of Contributions

By submitting a contribution, you agree that it will be licensed under the [Apache License 2.0](LICENSE), consistent with Section 5 of that license. No separate CLA is required.

## Questions

Open a [GitHub issue](https://github.com/inno-dpp/portal-for-circularity/issues) for questions about the codebase or contribution process.
