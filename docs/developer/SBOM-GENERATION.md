# SBOM Generation Guide

## Overview

The project uses **CycloneDX Maven Plugin** to generate Software Bill of Materials (SBOM) files. An SBOM is a complete inventory of all software components and dependencies in the application.

---

## Generating the SBOM

```bash
mvn cyclonedx:makeAggregateBom
```

**Output files:**
- `target/sbom.json` - CycloneDX JSON format
- `target/sbom.xml` - CycloneDX XML format

---

## Configuration

The plugin is configured in `pom.xml` with the following settings:

| Setting | Value | Description |
|---------|-------|-------------|
| `projectType` | application | Marks output as an application (not library) |
| `schemaVersion` | 1.5 | CycloneDX schema version |
| `includeCompileScope` | true | Include compile dependencies |
| `includeRuntimeScope` | true | Include runtime dependencies |
| `includeProvidedScope` | true | Include provided dependencies |
| `includeTestScope` | false | Exclude test dependencies |
| `outputFormat` | all | Generate both JSON and XML |

---

## Use Cases

### 1. Vulnerability Scanning

Scan dependencies for known CVEs using [Grype](https://github.com/anchore/grype):

```bash
# Install Grype (one-time)
curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sh -s -- -b /usr/local/bin

# Scan SBOM for vulnerabilities
grype sbom:target/sbom.json
```

Alternative tools:
- [Dependency-Track](https://dependencytrack.org/) - Web-based platform for continuous SBOM analysis
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/) - OWASP vulnerability scanner

### 2. License Compliance

List all licenses in dependencies:

```bash
jq -r '.components[] | "\(.name): \(.licenses[0].license.id // "Unknown")"' target/sbom.json
```

Get unique licenses:

```bash
jq -r '[.components[].licenses[]?.license.id] | unique | .[]' target/sbom.json
```

### 3. Dependency Inventory

Count total dependencies:

```bash
jq '.components | length' target/sbom.json
```

Search for a specific library (e.g., checking for Log4j):

```bash
jq '.components[] | select(.name | contains("log4j"))' target/sbom.json
```

List all Spring components:

```bash
jq -r '.components[] | select(.group == "org.springframework") | .name' target/sbom.json
```

### 4. Supply Chain Security

- **Regulatory Compliance** - Required by US Executive Order 14028 for federal software
- **Customer Requirements** - Enterprise customers may request SBOMs
- **Incident Response** - Quickly identify affected components during security events

---

## Integration with CI/CD

To generate SBOM during builds, add to your CI pipeline:

```bash
mvn cyclonedx:makeAggregateBom
```

The SBOM files can then be:
- Archived as build artifacts
- Uploaded to vulnerability scanning platforms
- Stored for compliance records

---

## Troubleshooting

### SBOM not generated
**Check:** Maven build succeeds first
```bash
mvn compile
mvn cyclonedx:makeAggregateBom
```

### Missing dependencies in SBOM
**Cause:** Dependency scope excluded by configuration
**Solution:** Check `includeXScope` settings in `pom.xml`

### Large SBOM file size
**Cause:** Includes transitive dependencies (expected behavior)
**Note:** The SBOM captures the complete dependency tree, not just direct dependencies

---

## Quick Reference

```bash
# Generate SBOM
mvn cyclonedx:makeAggregateBom

# View component count
jq '.components | length' target/sbom.json

# Scan for vulnerabilities (requires Grype)
grype sbom:target/sbom.json

# List all dependencies
jq -r '.components[] | "\(.group):\(.name):\(.version)"' target/sbom.json

# Check for specific library
jq '.components[] | select(.name | contains("LIBRARY_NAME"))' target/sbom.json
```

---

## References

- [CycloneDX Maven Plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin)
- [CycloneDX Specification](https://cyclonedx.org/specification/overview/)
- [NTIA SBOM Minimum Elements](https://www.ntia.gov/page/software-bill-materials)
- [Grype Vulnerability Scanner](https://github.com/anchore/grype)
