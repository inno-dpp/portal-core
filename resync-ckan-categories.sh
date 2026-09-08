#!/bin/bash
# Resync CKAN Category Membership Script (Linux/Mac/Git Bash)
#
# Grants every active CKAN category (see /admin/categories) to every already-onboarded
# organization's CKAN account. New onboardings get this automatically, and so does creating a
# new category (it's granted to existing organizations right when it's created) - this script is
# only needed once, after this feature is first deployed, to cover organizations onboarded before
# it existed (or as a catch-up if an automatic grant above logged an error for some organization).
#
# Safe to re-run any time - CKAN's own "already a member" response is treated as success.
# See docs/developer/ckan/RESYNC-EXISTING-ORGANIZATIONS.md for the full explanation.
#
# Usage:
#   PORTAL_BASE_URL=http://localhost:8085 \
#   PORTAL_ADMIN_USERNAME=admin \
#   PORTAL_ADMIN_PASSWORD='Demo_Admin#2025!' \
#   ./resync-ckan-categories.sh

set -euo pipefail

BASE_URL="${PORTAL_BASE_URL:-http://localhost:8085}"
USERNAME="${PORTAL_ADMIN_USERNAME:-admin}"
PASSWORD="${PORTAL_ADMIN_PASSWORD:-Local_Admin_5397cb7a0cd3}"

COOKIE_JAR="$(mktemp)"
BODY_FILE="$(mktemp)"
HEADERS_FILE="$(mktemp)"
trap 'rm -f "$COOKIE_JAR" "$BODY_FILE" "$HEADERS_FILE"' EXIT

echo "(using curl: $(command -v curl))"
echo "(cookie jar: $COOKIE_JAR)"
echo ""

# Issues one request, writes the body to $BODY_FILE, the full response/redirect header trace to
# $HEADERS_FILE (all hops, since -L follows redirects within this one curl invocation), and
# echoes the *final* HTTP status code. Never fails the script on a non-2xx status - callers
# decide what that means.
request() {
    local method="$1" url="$2"
    shift 2
    curl -sS -b "$COOKIE_JAR" -c "$COOKIE_JAR" -L -X "$method" \
        -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' "$url" "$@"
}

# Same as request(), but does NOT follow redirects - used for the login POST, which we only need
# the redirect status of (a successful login redirects to "/"; a failed one to "/login?error=...";
# either way we don't need to actually load whatever page it points to - the next step separately
# verifies the session against /admin/categories, the page we actually care about).
request_no_follow() {
    local method="$1" url="$2"
    shift 2
    curl -sS -b "$COOKIE_JAR" -c "$COOKIE_JAR" -X "$method" \
        -D "$HEADERS_FILE" -o "$BODY_FILE" -w '%{http_code}' "$url" "$@"
}

# Extracts Spring Security's hidden _csrf field value from $BODY_FILE, if present.
# Never fails (and so never trips `set -e`) even when there's no match.
extract_csrf() {
    grep -oE 'name="_csrf" value="[^"]+"' "$BODY_FILE" | head -1 | sed -E 's/.*value="([^"]+)"/\1/' || true
}

looks_like_login_page() {
    grep -q 'id="loginSubmitBtn"' "$BODY_FILE"
}

# Prints diagnostics (cookie jar + full header trace across redirects + response body snippet)
# to stderr on any unexpected outcome.
dump_diagnostics() {
    echo "      --- cookie jar contents ---" >&2
    cat "$COOKIE_JAR" >&2
    echo "      --- response headers (all hops) ---" >&2
    cat "$HEADERS_FILE" >&2
    echo "      --- response body (first 500 chars) ---" >&2
    head -c 500 "$BODY_FILE" >&2
    echo "" >&2
    echo "      ----------------------------------------" >&2
}

echo "====================================="
echo " Resync CKAN Category Membership"
echo "====================================="
echo ""
echo "Target: $BASE_URL"
echo ""

echo "[1/4] Loading login page..."
STATUS=$(request GET "$BASE_URL/login")
if [ "$STATUS" != "200" ]; then
    echo "      Got HTTP $STATUS loading $BASE_URL/login - is the app running and PORTAL_BASE_URL correct?" >&2
    dump_diagnostics
    exit 1
fi
CSRF=$(extract_csrf)
if [ -z "$CSRF" ]; then
    echo "      Reached $BASE_URL/login (HTTP 200) but found no CSRF token in the page - unexpected." >&2
    dump_diagnostics
    exit 1
fi

echo "[2/4] Logging in as '$USERNAME'..."
STATUS=$(request_no_follow POST "$BASE_URL/login" \
    --data-urlencode "username=$USERNAME" \
    --data-urlencode "password=$PASSWORD" \
    --data-urlencode "_csrf=$CSRF")
LOCATION=$(grep -i '^Location:' "$HEADERS_FILE" | tail -1 | tr -d '\r' | sed -E 's/^[Ll][Oo][Cc][Aa][Tt][Ii][Oo][Nn]: ?//')
if [ "$STATUS" != "302" ] && [ "$STATUS" != "303" ]; then
    echo "      Got HTTP $STATUS submitting login credentials - expected a redirect (302)." >&2
    dump_diagnostics
    exit 1
fi
if echo "$LOCATION" | grep -q '/login'; then
    echo "      Login redirected back to /login (${LOCATION}) - credentials were rejected." >&2
    echo "      Check PORTAL_ADMIN_USERNAME / PORTAL_ADMIN_PASSWORD (currently username='$USERNAME')." >&2
    dump_diagnostics
    exit 1
fi
echo "      Login accepted (redirected to $LOCATION)."

echo "[3/4] Confirming the session is authenticated as PLATFORM_ADMIN..."
STATUS=$(request GET "$BASE_URL/admin/categories")
if looks_like_login_page; then
    echo "      Redirected back to the login page loading /admin/categories - the session isn't" >&2
    echo "      authenticated (login silently failed) or cookies aren't being kept between requests." >&2
    dump_diagnostics
    exit 1
fi
if [ "$STATUS" = "403" ]; then
    echo "      HTTP 403 loading /admin/categories - '$USERNAME' is logged in but is not a" >&2
    echo "      PLATFORM_ADMIN. Use a platform admin account." >&2
    dump_diagnostics
    exit 1
fi
if [ "$STATUS" != "200" ]; then
    echo "      Got HTTP $STATUS loading /admin/categories (expected 200)." >&2
    dump_diagnostics
    exit 1
fi
CSRF2=$(extract_csrf)
if [ -z "$CSRF2" ]; then
    echo "      Reached /admin/categories (HTTP 200) but found no CSRF token in the page - unexpected." >&2
    dump_diagnostics
    exit 1
fi

echo "[4/4] Triggering the resync..."
# Same reasoning as the login step: the controller does its work and returns a redirect *before*
# curl would follow it, so the 302 status alone already proves the resync ran - no need to chase
# the redirect target (which is exactly the request pattern that 403s in this environment).
STATUS=$(request_no_follow POST "$BASE_URL/admin/categories/resync-organizations" \
    --data-urlencode "_csrf=$CSRF2")
if [ "$STATUS" != "302" ] && [ "$STATUS" != "303" ]; then
    echo "      Got HTTP $STATUS triggering the resync (expected a redirect, 302)." >&2
    dump_diagnostics
    exit 1
fi

echo ""
echo "Resync triggered successfully."
echo ""
echo "Verify by either:"
echo "  - Reloading $BASE_URL/admin/categories in a browser (flash message at the top), or"
echo "  - Checking the app logs for a line like:"
echo "      Group membership backfill processed N organization(s)"
echo ""
