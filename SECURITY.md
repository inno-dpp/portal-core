# Security Policy

## Supported Versions

Portal for Circularity is under active development. Security fixes are applied to
the latest release line only:

| Version | Supported |
|---------|-----------|
| Latest release (`master`) | ✅ |
| Latest beta prerelease (`develop`) | ✅ |
| Older releases | ❌ |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, report them privately via
[GitHub Security Advisories](https://github.com/inno-dpp/portal-for-circularity/security/advisories/new)
("Report a vulnerability" on the repository's Security tab).

Please include as much of the following as you can:

- A description of the vulnerability and its impact
- Steps to reproduce, or a proof of concept
- Affected version(s) or commit
- Any suggested remediation

## What to Expect

- We will acknowledge your report within **5 business days**.
- We will keep you informed of progress toward a fix and coordinate a
  disclosure timeline with you.
- Please give us a reasonable time to remediate before any public disclosure.

## Scope Notes

- Default development credentials (sample users seeded by the `dev` profile,
  H2 console access) are intentional for local development and are **not**
  considered vulnerabilities, provided they are not active under the
  production profile.
- Vulnerabilities in third-party dependencies should be reported upstream, but
  feel free to notify us as well so we can update the affected dependency.

Thank you for helping keep the project and its users safe.
