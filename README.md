# Portal for Circularity

[![CI](https://github.com/inno-dpp/portal-for-circularity/actions/workflows/ci.yml/badge.svg)](https://github.com/inno-dpp/portal-for-circularity/actions/workflows/ci.yml)
[![Build and Push](https://github.com/inno-dpp/portal-for-circularity/actions/workflows/build-push.yml/badge.svg)](https://github.com/inno-dpp/portal-for-circularity/actions/workflows/build-push.yml)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/inno-dpp/portal-for-circularity)](https://github.com/inno-dpp/portal-for-circularity/releases)
[![Docker Image](https://img.shields.io/badge/docker-ghcr.io-blue)](https://github.com/inno-dpp/portal-for-circularity/pkgs/container/portal-for-circularity)
[![Java](https://img.shields.io/badge/java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A Spring Boot web application integrating data catalog functionality with SPIP platform capabilities for circular economy data management. The portal enables organizations to discover, share, and securely manage data with attribute-based access control.

## Features

- **Organization Management**: Register and manage organizations, members, and roles
- **Data Catalog**: Browse and discover datasets across circular economy use cases
- **Connector Management**: Configure data providers, consumers, and SPIP agents
- **SPIP Integration**: Secure attribute-based access control and policy management
- **Onboarding Workflow**: Streamlined process for new organizations to join the data space
- **Role-Based Access**: Four-tier permission system (Platform Admin, Org Admin, SPIP User, Member)
- **White-Label Support**: Client-specific branding via configuration (brand name, colors, logos) - no code changes required
- **Observability**: Integrated with SigNoz for distributed tracing, metrics, and logs (no code changes required)

## White-Label Deployment

Deploy client-specific branded instances without code changes. Customize brand name, colors, logos, and typography through configuration.

### Quick Start Examples

**Option 1: Application Configuration (Development)**
```yaml
# application.yml
app:
  branding:
    brand-name: "Your Company Name"
    brand-tagline: "Your Tagline"
    colors:
      primary: "#1E5AA8"
      secondary: "#2F3E4E"
      accent: "#F2B705"
```

**Option 2: Environment Variables (Production)**
```bash
export BRANDING_NAME="Your Company Name"
export BRANDING_COLOR_PRIMARY="#1E5AA8"
export BRANDING_COLOR_SECONDARY="#2F3E4E"
export BRANDING_COLOR_ACCENT="#F2B705"
```

**Option 3: Docker Compose**
```yaml
services:
  app:
    environment:
      BRANDING_NAME: "Your Company Name"
      BRANDING_TAGLINE: "Your Tagline"
      BRANDING_COLOR_PRIMARY: "#1E5AA8"
      BRANDING_COLOR_SECONDARY: "#2F3E4E"
      BRANDING_COLOR_ACCENT: "#F2B705"
```

### Example Themes

**Corporate Blue**
```bash
BRANDING_NAME="Your Company Portal"
BRANDING_COLOR_PRIMARY="#1E5AA8"    # Corporate blue
BRANDING_COLOR_SECONDARY="#2F3E4E"  # Slate
BRANDING_COLOR_ACCENT="#F2B705"     # Amber
```

**Eco Green Theme**
```bash
BRANDING_NAME="EcoMaterials Exchange"
BRANDING_COLOR_PRIMARY="#2E7D32"    # Forest green
BRANDING_COLOR_SECONDARY="#1B5E20"  # Dark green
BRANDING_COLOR_ACCENT="#81C784"     # Light green
```

**Industrial Gray Theme**
```bash
BRANDING_NAME="Industrial Materials Hub"
BRANDING_COLOR_PRIMARY="#455A64"    # Blue-gray
BRANDING_COLOR_SECONDARY="#263238"  # Dark gray
BRANDING_COLOR_ACCENT="#FF6F00"     # Deep orange
```

### Customizable Elements

- **Brand Identity**: Name, tagline, subtagline
- **Colors**: Primary, secondary, accent, success, warning, danger, text (8 colors)
- **Typography**: Font families for body and headings
- **Assets**: Logos (full & small), favicon, optional login background
- **Navbar**: Background and text colors

For complete deployment guide, see [White-Label Deployment Documentation](docs/developer/WHITE-LABEL-DEPLOYMENT.md).

## Technology Stack

- **Backend**: Spring Boot 3.x, Spring Security, Spring Data JPA
- **Frontend**: Thymeleaf, Bootstrap 5, JavaScript
- **Database**: PostgreSQL (production), H2 (development)
- **Authentication**: JWT-based with role-based access control
- **Build**: Maven
- **Deployment**: Docker, GitHub Actions CI/CD

## CI/CD Pipeline

This project uses GitHub Actions for continuous integration and deployment:

- **CI Workflow**: Automated testing on all pushes and pull requests
- **Build & Push**: Builds Docker images and pushes to GitHub Container Registry
- **Semantic Versioning**: Automatic version management based on conventional commits

## Contributing

Please read the [Contributing Guide](CONTRIBUTING.md) for development setup, coding guidelines, and the full workflow. Quick version:

1. Create a feature branch: `git checkout -b feature/my-feature`
2. Commit changes using [conventional commits](https://www.conventionalcommits.org/)
4. Push to the branch: `git push origin feature/my-feature`
5. Create a Pull Request to devel branch

### Example Conventional Commits

```bash
# Feature (minor version bump)
git commit -m "feat: add user authentication"

# Bug fix (patch version bump)
git commit -m "fix: resolve login timeout"

# Breaking change (major version bump)
git commit -m "feat!: redesign API structure"
```

### Commit Message Cheat Sheet

| What you're doing | Commit message | Version bump |
|-------------------|----------------|--------------|
| New feature | `feat: add SPIP integration` | 1.0.0 → 1.1.0 |
| Bug fix | `fix: resolve auth issue` | 1.0.0 → 1.0.1 |
| Performance | `perf: optimize queries` | 1.0.0 → 1.0.1 |
| Refactoring | `refactor: simplify code` | 1.0.0 → 1.0.1 |
| Breaking change | `feat!: redesign API` | 1.0.0 → 2.0.0 |
| Documentation | `docs: update guide` | No release |
| Tests | `test: add unit tests` | No release |
| Maintenance | `chore: update deps` | No release |

### Pro Tips

- Use `[skip ci]` in commit message to skip CI: `git commit -m "docs: update [skip ci]"`
- Use scopes for better organization: `feat(auth): add OAuth2`
- Keep commits atomic (one logical change per commit)
- Write meaningful commit bodies explaining "why"
- Use `git commit --amend` to fix commit messages before pushing
- Check Actions tab regularly for github workflow status

## Documentation

### Deployment Guides
- [White-Label Deployment](docs/developer/WHITE-LABEL-DEPLOYMENT.md) - **Client-specific branding configuration guide**
- [Production Deployment](docs/developer/PRODUCTION-DEPLOYMENT.md) - Production setup guide

### Developer Guides
- [Database Setup](docs/developer/DATABASE-SETUP.md) - **Development database configuration and reset instructions**
- [Email Configuration](docs/developer/EMAIL-CONFIGURATION.md) - Email service setup
- [SigNoz Telemetry](docs/developer/observability-signoz/SIGNOZ-TELEMETRY.md) - Observability and monitoring with SigNoz

### Observability
- [SigNoz Quick Start](docs/developer/observability-signoz/SIGNOZ-QUICKSTART.md) - **Get started with distributed tracing, metrics, and logs**

### CI/CD & Workflows
- [GitHub Actions Setup](docs/developer/cicd/GITHUB-ACTIONS-SETUP.md) - CI/CD configuration guide
- [Onboarding Workflow](docs/developer/others/ONBOARDING-WORKFLOW.md) - Organization onboarding

### Quick Database Reset
```bash
# Windows
reset-dev-database.bat

# Linux/Mac
chmod +x reset-dev-database.sh
./reset-dev-database.sh
```

## License

This project is licensed under the [Apache License, Version 2.0](LICENSE).

Copyright 2024-2026 NTT DATA Romania. See the [NOTICE](NOTICE) file for attribution details.

## Support

For issues, questions, or contributions:
- Create an [issue](https://github.com/inno-dpp/portal-for-circularity/issues)
- Check the [documentation](docs/)

---

**Built with** ☕ and Spring Boot | Originally developed in the DATA4CIRC EU project
