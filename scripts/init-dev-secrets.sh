#!/usr/bin/env bash
# Creates the local-development federation secret shared by domain-a and domain-b.
# The file lives under server/data/ (git-ignored) and is created only if missing.
# Production/compose deployments inject secrets via environment variables instead.
set -euo pipefail

DIR="$(cd "$(dirname "$0")/../server" && pwd)/data/federation-dev"
FILE="$DIR/domain-a.test__domain-b.test.secret"

mkdir -p "$DIR"
chmod 700 "$DIR"
if [[ ! -s "$FILE" ]]; then
  umask 077
  openssl rand -base64 48 | tr -d '\n' > "$FILE"
  echo "Generated federation dev secret: $FILE"
fi
