#!/usr/bin/env bash
# Start one mail domain instance locally (embedded H2, no Docker needed).
# Usage: scripts/run-local.sh domain-a|domain-b [tls]
#   tls  serve HTTPS and talk HTTPS to the peer (run scripts/gen-dev-certs.sh first)
# Data lives in server/data/<domain>/ and is never shared between instances.
set -euo pipefail

DOMAIN="${1:-}"
MODE="${2:-}"
case "$DOMAIN" in
  domain-a|domain-b) ;;
  *) echo "Usage: $0 domain-a|domain-b [tls]" >&2; exit 64 ;;
esac

SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$(cd "$SCRIPTS/../server" && pwd)"

PROFILES="local,${DOMAIN}"
if [[ "$MODE" == "tls" ]]; then
  if [[ ! -s "$SERVER_DIR/data/certs/ca.crt" ]]; then
    echo "TLS certificates missing: run scripts/gen-dev-certs.sh first" >&2
    exit 1
  fi
  PROFILES="${PROFILES},tls"
fi

# Shared federation secret for the local domain-a <-> domain-b pair (created once).
"$SCRIPTS/init-dev-secrets.sh"

# Prefer JDK 21 on macOS if JAVA_HOME is not set.
if [[ -z "${JAVA_HOME:-}" ]] && [[ -x /usr/libexec/java_home ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  export JAVA_HOME
fi

cd "$SERVER_DIR"
exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles="$PROFILES"
