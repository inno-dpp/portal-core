# GitHub Copilot Instructions for DATA4CIRC Portal

This file provides guidance to GitHub Copilot when working with code in this repository.

## Project Overview

DATA4CIRC Portal is a Spring Boot 3.x web application that integrates data catalog functionality with SPIP (Secure Privacy-Preserving Infrastructure Platform) capabilities for circular economy data management. The portal enables organizations to discover, share, and securely manage data with attribute-based access control.

### Technology Stack
- **Backend**: Java 17, Spring Boot 3.2.0, Spring Security, Spring Data JPA
- **Frontend**: Thymeleaf templates, Bootstrap 5, JavaScript
- **Database**: PostgreSQL (production), H2 (development/test)
- **Authentication**: JWT-based with role-based access control
- **Build Tool**: Maven
- **Deployment**: Docker, GitHub Actions CI/CD

## Project Structure & Module Organization

- Backend code lives in `src/main/java/com/data4circ/portal`, organized by domain packages:
  - `controller/` - Spring MVC controllers handling web requests
  - `service/` - Business logic and transaction management
  - `repository/` - Spring Data JPA repositories
  - `entity/` - JPA entities representing domain model
  - `dto/` - Data transfer objects
  - `security/` - Security components (JWT, filters, authentication)
  - `config/` - Spring configuration classes
  - `ckan/` - CKAN data catalog integration
  - `spip/` - SPIP platform integration
  
- Frontend templates and static assets:
  - `src/main/resources/templates/` - Thymeleaf templates
  - `src/main/resources/static/` - CSS, JavaScript, images
  - `src/main/resources/templates/fragments/` - Reusable template fragments

- Configuration:
  - `src/main/resources/application.yml` - Default configuration
  - `src/main/resources/application-dev.yml` - Development profile (H2 database)
  - `src/main/resources/application-prod.yml` - Production profile (PostgreSQL)
  - `src/main/resources/application-test.yml` - Test profile

- Tests:
  - `src/test/java/com/data4circ/portal/` - Mirrors production code structure
  - `src/test/java/com/data4circ/portal/service/` - Service layer unit tests
  - `src/test/java/com/data4circ/portal/integration/` - Integration tests

- Docker and deployment:
  - `docker/` - Docker-related configuration files
  - `docker-compose.local.yml` - Local full stack (Postgres + app built from source)
  - `docker-compose.prod.yml` - Production environment (pulled image)
  - `Dockerfile` - Application container definition

- Documentation:
  - `docs/` - Comprehensive documentation
  - `docs/developer/` - Developer guides
  - `docs/cicd/` - CI/CD documentation
  - `docs/features/` - Feature documentation
  - `docs/architecture/` - Architecture documentation

## Build, Test, and Development Commands

### Building the Application
```bash
# Full build with tests
mvn clean verify

# Compile only
mvn clean compile

# Package without tests
mvn clean package -DskipTests

# The build produces a JAR in target/
```

### Running the Application
```bash
# Run with development profile (H2 in-memory database)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Local full stack via Docker (PostgreSQL, prod profile)
docker compose -f docker-compose.local.yml up --build
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=OrganizationControllerTest

# Run tests with specific profile
mvn test -Dspring.profiles.active=test

# Integration tests
mvn verify
```

### Docker Commands
```bash
# Start application with PostgreSQL for local development
docker compose -f docker-compose.local.yml up --build

# Start production environment
docker compose -f docker-compose.prod.yml --env-file .env up -d

# Stop containers
docker compose -f docker-compose.local.yml down

# View logs
docker compose -f docker-compose.local.yml logs -f
```

### Database Management
```bash
# Quick database reset (development)
# Windows:
reset-dev-database.bat

# Linux/Mac:
chmod +x reset-dev-database.sh
./reset-dev-database.sh
```

### H2 Console Access (Development)
- URL: http://localhost:8085/h2-console
- JDBC URL: `jdbc:h2:mem:d4c_portal_dev`
- Username: `sa`
- Password: (empty)

## Coding Style & Naming Conventions

### General Java Style
- Use Java 17 features where appropriate
- Prefer 4-space indentation
- Use K&R brace style (opening brace on same line)
- Format code with IDE's Google Java Style or IntelliJ defaults
- Keep imports organized and remove unused imports

### Spring Boot Conventions
- Controllers: Suffix with `Controller` (e.g., `OrganizationController`)
- Services: Suffix with `Service` (e.g., `OrganizationService`)
- Repositories: Suffix with `Repository` (e.g., `OrganizationRepository`)
- Configuration: Suffix with `Config` (e.g., `SecurityConfig`)
- DTOs: Place in `dto/` package, clear naming (e.g., `OrganizationDTO`, `CreateUserRequest`)

### Package Organization
- Group related functionality together
- Keep package-private visibility for internal classes
- Use public interfaces for service contracts

### Thymeleaf Templates
- Keep templates small and focused
- Use fragments for reusable components
- Place shared fragments in `templates/fragments/`
- Follow Bootstrap 5 conventions for styling
- Use Thymeleaf security expressions for role-based rendering

## Testing Guidelines

### Test Framework
- Use JUnit 5 as the testing framework
- Use Spring Boot Test (`spring-boot-starter-test`)
- Use Spring Security Test for security testing

### Test Naming and Organization
- Test classes should end with `Test` (e.g., `OrganizationServiceTest`)
- Mirror the production code package structure in test directories
- Group tests by functionality using nested test classes with `@Nested`

### Test Types
- **Unit Tests**: Test service layer logic in isolation
  - Mock dependencies using Mockito
  - Focus on business logic and edge cases
  - Place in `src/test/java/.../service/` package
  
- **Integration Tests**: Test complete request flows
  - Use `@SpringBootTest` and `@AutoConfigureMockMvc`
  - Test controller endpoints with MockMvc
  - Place in `src/test/java/.../integration/` package

### Test Profile
- Always use the `test` profile for running tests
- The `application-test.yml` provides deterministic test configuration
- Use H2 in-memory database for fast test execution
- Mock external service calls to SPIP and CKAN endpoints

### Test Coverage
- New features must include tests covering:
  - Happy path (expected successful operation)
  - At least one failure/error case
  - Edge cases (null inputs, empty collections, etc.)
  - Authorization checks for secured endpoints

### Example Test Structure
```java
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private OrganizationService organizationService;
    
    @Test
    void shouldCreateOrganization() {
        // Test implementation
    }
    
    @Test
    void shouldReturnErrorWhenInvalidInput() {
        // Test implementation
    }
}
```

## Commit & Pull Request Guidelines

### Conventional Commits
Use conventional commit format for all commits:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

**Types:**
- `feat`: New feature (minor version bump)
- `fix`: Bug fix (patch version bump)
- `perf`: Performance improvement (patch version bump)
- `refactor`: Code refactoring (patch version bump)
- `docs`: Documentation only changes (no version bump)
- `test`: Adding or updating tests (no version bump)
- `chore`: Maintenance tasks (no version bump)
- `feat!` or `fix!`: Breaking changes (major version bump)

**Examples:**
```bash
feat: add organization onboarding workflow
fix: resolve JWT token expiration issue
feat!: redesign connector API structure
docs: update database setup guide
test: add integration tests for SPIP endpoints
chore: bump Spring Boot to 3.2.1
```

### Commit Best Practices
- Keep commits small and focused on a single logical change
- Write clear, descriptive commit messages
- Include "why" explanation in commit body for non-trivial changes
- Use `[skip ci]` in commit message to skip CI when appropriate

### Pull Request Process
1. Create feature branch from `devel`: `git checkout -b feature/my-feature`
2. Make changes following coding guidelines
3. Write/update tests for your changes
4. Ensure all tests pass: `mvn test`
5. Commit using conventional commit format
6. Push to your branch: `git push origin feature/my-feature`
7. Create Pull Request targeting `devel` branch

### Pull Request Requirements
- Concise title and description
- Link to related issue(s)
- Test evidence (successful `mvn test` output or docker-compose validation)
- Screenshots for UI changes
- Ensure CI passes before requesting review
- Address review comments promptly

## Security & Environment Notes

### Security Best Practices
- Never commit secrets or sensitive credentials
- Use environment variables for configuration
- Store sensitive config in `.env` file (not committed to git)
- Use `.env.example` as template for required environment variables
- Validate all user inputs in controllers
- Use `@PreAuthorize` annotations for role-based access control
- Sanitize data before rendering in templates

### Environment Variables
Key environment variables (see `.env.example`):
- `SPIP_*` - SPIP platform integration settings
- `CKAN_*` - CKAN data catalog settings
- Database connection settings (for the prod profile)

### Spring Profiles
- `dev`: H2 in-memory database (port 8085), suitable for quick development
- `prod`: PostgreSQL; used by both docker-compose files and production
- `test`: Test configuration with H2, for automated testing

Set profile via: `-Dspring.profiles.active=<profile>`

### User Roles
The application uses four-tier role-based access control:
- `PLATFORM_ADMIN`: System-wide administration
- `ORG_ADMIN`: Organization management
- `SPIP_PRIVILEGED_USER`: SPIP platform access and policy management
- `ORG_MEMBER`: Basic organization member

Always consider role requirements when implementing new features.

## Common Development Patterns

### Adding a New Entity
1. Create JPA entity in `entity/` package with proper annotations
2. Create repository interface in `repository/` extending `JpaRepository`
3. Create service class in `service/` for business logic
4. Create DTO classes in `dto/` for API contracts
5. Create controller in `controller/` for HTTP endpoints
6. Add Thymeleaf templates in `templates/` if needed
7. Write tests for service and controller layers

### Integrating External Services
1. Create integration client in appropriate package (`ckan/`, `spip/`, etc.)
2. Add configuration properties in `application.yml`
3. Implement error handling and retry logic
4. Add integration tests with mocked external services
5. Document integration in `docs/` directory

### Adding New Web Pages
1. Create controller method with appropriate mapping
2. Create Thymeleaf template in `templates/`
3. Use existing fragments from `templates/fragments/` for consistency
4. Add security annotations if authentication required
5. Update navigation menu in relevant fragment
6. Add integration tests for the new endpoint

## CI/CD Pipeline

### GitHub Actions Workflows
- `.github/workflows/ci.yml` - Runs tests on push/PR
- `.github/workflows/build-push.yml` - Builds and pushes Docker images

### Version Management
- Semantic versioning based on conventional commits
- Automated version bumping on merge to main
- Release notes generated automatically

### Before Requesting Review
- Ensure local tests pass: `mvn test`
- Check CI status in GitHub Actions tab
- Fix any linting or build issues
- Verify docker-compose builds if infrastructure changes made

## Additional Resources

- **README.md**: Project overview and quick start
- **AGENTS.md**: Detailed repository guidelines
- **docs/**: Comprehensive documentation
  - `docs/developer/DATABASE-SETUP.md`: Database configuration
  - `docs/developer/PRODUCTION-DEPLOYMENT.md`: Production deployment guide
  - `docs/cicd/GITHUB-ACTIONS-SETUP.md`: CI/CD configuration
  - `docs/features/ONBOARDING-WORKFLOW.md`: Onboarding workflow details

## Quick Reference

```bash
# Start development server
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Run tests
mvn test

# Build JAR
mvn clean package

# Start with Docker (local full stack)
docker compose -f docker-compose.local.yml up --build

# Reset dev database
./reset-dev-database.sh
```

---

**Note**: This file is specifically for GitHub Copilot. For Claude Code instructions, see `CLAUDE.md`. For general repository guidelines, see `AGENTS.md`.
