# Resync CKAN Category Membership for Existing Organizations

## What this is for

New onboardings automatically grant the onboarded user's CKAN account editor membership on
every active category (`CkanOnboardingSyncService.addUserToUseCaseGroupsIdempotent`, step 3b of
onboarding sync). Organizations onboarded **before** that step existed never got that grant, and
won't get it retroactively on their own. Their CKAN token still works for everything else; it
just isn't a member of any CKAN group, so an attempt to attach a dataset to a category (e.g. via
documanager's publish form) fails with `403 Access denied: User <x> not authorized to edit these
groups`.

This is the known, already-documented gap described in
[CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md](./CKAN-GROUP-MEMBERSHIP-FOR-ONBOARDED-USERS.md#rollout-for-already-onboarded-organizations).
Running the script below closes it.

**Regenerating a connector's CKAN token does not fix this** — group membership is tied to the
CKAN *username*, not to which token string is currently active. Only this resync (or a manual
grant in CKAN) fixes it.

**Safe to re-run.** Every organization and every category is processed independently and
idempotently — CKAN's own "already a member" response is treated as success, so running this
twice never causes an error or a duplicate grant. It only processes organizations that actually
have a working CKAN account (`OrganizationSpipUser.ckanSynchronized == true`); organizations that
never finished CKAN provisioning are skipped.

## When to run this

**New categories no longer need this.** Creating a category at `/admin/categories` already
grants it to every already-onboarded organization automatically
(`CategoryService.create()` → `CkanOnboardingSyncService.grantCategoryToAllOrganizations()`) —
that happens synchronously as part of saving the category, not as a separate manual step.

So in practice you only need to run this script:

- **Once**, right after this feature is first deployed, to cover every organization that was
  onboarded *before* it existed.
- **As a catch-up**, if the automatic per-organization grant above logged errors for some
  organizations when a category was created (e.g. CKAN was briefly unreachable at that moment) —
  re-running this script retries every organization against every category, so it also covers
  anything the automatic grant missed.

There is **no UI button for this** — it's intentionally a command-line/deploy-time action, not
something clicked repeatedly from the admin screen (the automatic grant-on-create above is what
handles the day-to-day case).

## Running it

There's no separate backend script exposed as a UI action — the resync is a normal admin
endpoint (`POST /admin/categories/resync-organizations`), protected the same way every other
admin action in this app is: an authenticated Platform Admin session plus a CSRF token.
**[`resync-ckan-categories.sh`](../../../resync-ckan-categories.sh)** (repo root) drives that
endpoint the same way a browser would — log in, grab a CSRF token, POST — so you can trigger it
from a terminal or a deploy pipeline.

### Steps

1. Make sure the portal app is running and reachable (e.g. `http://localhost:8085` in dev, or
   your deployed URL) and CKAN itself is reachable from the portal (this resync makes live
   `group_member_create` calls to CKAN — if CKAN is down, the run will complete but log
   per-organization warnings for the calls that failed; safe to re-run once CKAN is back).
2. Have the username/password of a **Platform Admin** account (e.g. the seeded `admin` account
   in dev, or whatever `ADMIN_USERNAME`/`ADMIN_PASSWORD` bootstrapped in prod).
3. From the repo root, run:

   ```bash
   PORTAL_BASE_URL=http://localhost:8085 \
   PORTAL_ADMIN_USERNAME=admin \
   PORTAL_ADMIN_PASSWORD='Demo_Admin#2025!' \
   ./resync-ckan-categories.sh
   ```

   (Swap in your real base URL and admin credentials — the values above are the dev-profile
   sample admin per `CLAUDE.md`, not a production credential.)

4. The script prints its own step-by-step progress (`[1/4]` … `[4/4]`) and exits non-zero with a
   clear message if login fails (wrong credentials, app unreachable, or the account isn't a
   platform admin).

### Verifying it worked

- **Portal side**: reload `/admin/categories` in a browser, or check the app logs for:
  ```
  Group membership backfill processed N organization(s)
  ```
- **CKAN side**: for a specific organization's CKAN username, `GET
  /api/3/action/member_list?id=<category-slug>` on CKAN should now list that username for every
  active category.
- **End-to-end**: publish a dataset through documanager's `/edc/publish` form with a Use-Case
  Category selected, using that organization's own token — it should now succeed instead of
  403ing.

## What actually happens under the hood

The script calls `CkanOnboardingSyncService.backfillGroupMembershipForAllOrganizations()`: for
every `OrganizationSpipUser` row with `ckanSynchronized == true` (i.e. an org that actually has a
CKAN account), it grants that org's CKAN username `editor` membership on every active `Category`.
A failure granting one (org, category) pair is logged and skipped — it never blocks the rest of
that organization's categories, or the next organization.

This is distinct from — but shares the same underlying grant logic as —
`CkanOnboardingSyncService.grantCategoryToAllOrganizations(slug)`, which `CategoryService.create()`
calls automatically for just the one new category whenever an admin creates it, so that case
doesn't need this script at all.
