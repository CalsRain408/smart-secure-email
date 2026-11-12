#!/usr/bin/env bash
# Generates a development CA and TLS server certificates for domain-a and domain-b.
# Output: server/data/certs/{ca.crt,ca.key,domain-a.crt,domain-a.key,domain-b.crt,domain-b.key}
# (git-ignored). Use with the `tls` profile:  scripts/run-local.sh domain-a tls
# These certificates are for local demos only; never use them in production.
set -euo pipefail

OUT="$(cd "$(dirname "$0")/../server" && pwd)/data/certs"
mkdir -p "$OUT"
chmod 700 "$OUT"
cd "$OUT"
umask 077

if [[ -s ca.crt && -s ca.key && "${1:-}" != "--force" ]]; then
  echo "Certificates already exist in $OUT (use --force to regenerate)."
  exit 0
fi

openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 825 \
  -keyout ca.key -out ca.crt -subj "/CN=Smart Secure Mail Dev CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null

for d in a b; do
  name="domain-$d"
  openssl req -newkey rsa:2048 -nodes -sha256 \
    -keyout "$name.key" -out "$name.csr" -subj "/CN=$name.test" 2>/dev/null
  printf 'subjectAltName=DNS:localhost,DNS:%s.test,DNS:server-%s,IP:127.0.0.1\nbasicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n' \
    "$name" "$d" > "$name.ext"
  openssl x509 -req -in "$name.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
    -sha256 -days 397 -extfile "$name.ext" -out "$name.crt" 2>/dev/null
  rm -f "$name.csr" "$name.ext"
done
rm -f ca.srl

echo "Generated dev CA and server certificates in $OUT"
openssl x509 -in domain-a.crt -noout -subject -ext subjectAltName
