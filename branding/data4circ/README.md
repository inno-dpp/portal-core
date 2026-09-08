# DATA4CIRC branding bundle

The complete DATA4CIRC brand for this portal — name, texts, colors,
typography, logos, and dashboard use-case cards — as a runtime-attached
configuration bundle. The portal itself stays brand-neutral: nothing here is
active unless a deployment opts in.

This is an instance of the branding-overlay pattern described in
[docs/developer/WHITE-LABEL-DEPLOYMENT.md](../../docs/developer/WHITE-LABEL-DEPLOYMENT.md);
it can also serve as a template for other deployment brands
(`branding/<your-deployment>/`).

## Activate

**Host run** — one environment variable (e.g. in your git-ignored `.env`):

```
SPRING_CONFIG_IMPORT=optional:file:./branding/data4circ/branding.yml
```

**Docker stack** — add the committed override as a second compose file:

```bash
docker compose -f docker-compose.local.yml -f docker-compose.data4circ.yml up -d
```

Without either, the portal runs with its neutral defaults. Individual
`BRANDING_*` environment variables still override values from this bundle.

## Contents

```
branding.yml              # all app.branding.* properties, dashboard cards,
                          #   and the static-locations override that serves
                          #   the logo files below
assets/branding/*.png     # logos + favicon
```

## Deployment-specific values

The dashboard card URLs in `branding.yml` are placeholders
(`ckan.example.com`) — real catalog URLs are deployment infrastructure and
stay out of this public repository. Override them per deployment (e.g. via a
second `SPRING_CONFIG_IMPORT` entry or by maintaining the real bundle in a
private repo). The same applies to `support-email`.
