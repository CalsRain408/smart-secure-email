#!/usr/bin/env bash
# End-to-end check against RUNNING servers (real HTTP, real cookies, real federation).
#   1. Start the servers:   scripts/run-local.sh domain-a   (and domain-b in another terminal)
#   2. Run:                 scripts/e2e-local-mail.sh
# HTTPS (servers started with the `tls` argument):
#   A_URL=https://localhost:8081 B_URL=https://localhost:8082 \
#   CURL_CA=server/data/certs/ca.crt scripts/e2e-local-mail.sh
# Requires: curl, python3.
set -uo pipefail

A_URL="${A_URL:-http://localhost:8081}"
B_URL="${B_URL:-http://localhost:8082}"
CURL_TLS=()
[[ -n "${CURL_CA:-}" ]] && CURL_TLS=(--cacert "$CURL_CA")
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PASS=0; FAIL=0
SUFFIX="$(date +%s | tail -c 6)$RANDOM"
PASSWORD="E2eTestPassw0rd"

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
fail() { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; [[ -n "${2:-}" ]] && printf '       %s\n' "$2"; }

# json <file> <python-expression on `d`>
json() { python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print($2)" "$1" 2>/dev/null; }

# call <client> <method> <url> [json-body] -> prints HTTP status; body in $WORK/<client>.out
call() {
  local client="$1" method="$2" url="$3" body="${4:-}"
  local jar="$WORK/$client.jar" token
  token="$(cat "$WORK/$client.csrf" 2>/dev/null || true)"
  local args=(-s ${CURL_TLS[@]+"${CURL_TLS[@]}"} -o "$WORK/$client.out" -w '%{http_code}' -b "$jar" -c "$jar" -X "$method" "$url")
  [[ -n "$token" ]] && args+=(-H "X-CSRF-TOKEN: $token")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data "$body")
  curl "${args[@]}"
}

# refresh_csrf <client> <base-url>: fetch the session-bound CSRF token
refresh_csrf() {
  call "$1" GET "$2/api/v1/auth/csrf" >/dev/null
  json "$WORK/$1.out" 'd["data"]["token"]' > "$WORK/$1.csrf"
}

expect() { # expect <description> <expected-status> <actual-status> [client]
  if [[ "$2" == "$3" ]]; then ok "$1"; else fail "$1 (expected HTTP $2, got $3)" "$(cat "$WORK/${4:-x}.out" 2>/dev/null | head -c 300)"; fi
}

signup_and_login() { # <client> <base-url> <username>
  refresh_csrf "$1" "$2"
  local reg login
  reg=$(printf '{"username":"%s","displayName":"E2E %s","password":"%s","confirmPassword":"%s"}' "$3" "$3" "$PASSWORD" "$PASSWORD")
  login=$(printf '{"username":"%s","password":"%s"}' "$3" "$PASSWORD")
  expect "register $3" 201 "$(call "$1" POST "$2/api/v1/auth/register" "$reg")" "$1"
  expect "login $3" 200 "$(call "$1" POST "$2/api/v1/auth/login" "$login")" "$1"
  refresh_csrf "$1" "$2"   # token rotates on login
}

ALICE="alice$SUFFIX"; BOB="bob$SUFFIX"; CAROL="carol$SUFFIX"
echo "== Instances"
for u in "$A_URL" "$B_URL"; do
  if curl -sf ${CURL_TLS[@]+"${CURL_TLS[@]}"} "$u/api/v1/system/info" -o "$WORK/info.out"; then ok "$u serves $(json "$WORK/info.out" 'd["data"]["domain"]')"
  else fail "$u is not reachable"; echo "Start the servers first (scripts/run-local.sh)."; exit 2; fi
done

echo "== Accounts"
signup_and_login alice "$A_URL" "$ALICE"
signup_and_login bob   "$A_URL" "$BOB"
signup_and_login carol "$B_URL" "$CAROL"

# curl marks HttpOnly cookies with a "#HttpOnly_" prefix in its cookie jar.
if grep -q '^#HttpOnly_.*SSM_SESSION' "$WORK/alice.jar"; then ok "session cookie is HttpOnly"
else fail "session cookie is not HttpOnly"; fi

echo "== Local delivery (domain-a)"
BODY=$(printf '{"to":["%s@domain-a.test"],"subject":"E2E hello %s","body":"<script>alert(1)</script> plain text"}' "$BOB" "$SUFFIX")
expect "alice sends to bob" 201 "$(call alice POST "$A_URL/api/v1/mails" "$BODY")" alice
SENT_ID="$(json "$WORK/alice.out" 'd["data"]["id"]')"
[[ "$(json "$WORK/alice.out" 'd["data"]["delivery"][0]["status"]')" == "DELIVERED" ]] \
  && ok "delivery status DELIVERED" || fail "delivery status not DELIVERED"

expect "bob lists inbox" 200 "$(call bob GET "$A_URL/api/v1/mails?folder=INBOX")" bob
INBOX_ID="$(json "$WORK/bob.out" "[i['id'] for i in d['data']['items'] if i['subject']=='E2E hello $SUFFIX'][0]")"
[[ -n "$INBOX_ID" ]] && ok "bob received the mail (id=$INBOX_ID)" || fail "mail not in bob's inbox"

expect "bob opens the mail" 200 "$(call bob GET "$A_URL/api/v1/mails/$INBOX_ID")" bob
[[ "$(json "$WORK/bob.out" 'd["data"]["body"]')" == "<script>alert(1)</script> plain text" ]] \
  && ok "body returned verbatim as JSON text" || fail "body altered"

echo "== Drafts"
BODY=$(printf '{"to":["%s@domain-a.test"],"subject":"E2E draft %s","body":"v1"}' "$BOB" "$SUFFIX")
expect "alice saves a draft" 201 "$(call alice POST "$A_URL/api/v1/drafts" "$BODY")" alice
DRAFT_ID="$(json "$WORK/alice.out" 'd["data"]["id"]')"
BODY=$(printf '{"to":["%s@domain-a.test"],"subject":"E2E draft %s","body":"v2"}' "$BOB" "$SUFFIX")
expect "alice edits the draft" 200 "$(call alice PUT "$A_URL/api/v1/drafts/$DRAFT_ID" "$BODY")" alice
expect "alice sends the draft" 200 "$(call alice POST "$A_URL/api/v1/drafts/$DRAFT_ID/send")" alice

echo "== Cross-domain delivery (domain-a <-> domain-b)"
# await_delivery <client> <base-url> <sent-id> <recipient>: poll the author's sent copy
await_delivery() {
  local status="" i
  for i in $(seq 1 60); do
    call "$1" GET "$2/api/v1/mails/$3" >/dev/null
    status="$(json "$WORK/$1.out" "[x['status'] for x in d['data']['delivery'] if x['address']=='$4'][0]")"
    [[ "$status" == "DELIVERED" || "$status" == "FAILED" ]] && break
    sleep 0.25
  done
  echo "$status"
}
# find_inbox <client> <base-url> <subject>: prints the mailbox item id or nothing
find_inbox() {
  call "$1" GET "$2/api/v1/mails?folder=INBOX" >/dev/null
  json "$WORK/$1.out" "([i['id'] for i in d['data']['items'] if i['subject']=='$3'] or [''])[0]"
}

BODY=$(printf '{"to":["%s@domain-b.test"],"subject":"E2E cross %s","body":"Hello from A"}' "$CAROL" "$SUFFIX")
expect "alice@a sends to carol@b" 201 "$(call alice POST "$A_URL/api/v1/mails" "$BODY")" alice
X_ID="$(json "$WORK/alice.out" 'd["data"]["id"]')"
ST="$(await_delivery alice "$A_URL" "$X_ID" "$CAROL@domain-b.test")"
[[ "$ST" == "DELIVERED" ]] && ok "A->B delivery status DELIVERED" || fail "A->B delivery status: $ST"
CAROL_ITEM="$(find_inbox carol "$B_URL" "E2E cross $SUFFIX")"
[[ -n "$CAROL_ITEM" ]] && ok "carol received it on domain-b" || fail "mail missing in carol's inbox"
call carol GET "$B_URL/api/v1/mails/$CAROL_ITEM" >/dev/null
[[ "$(json "$WORK/carol.out" 'd["data"]["from"]["address"]')" == "$ALICE@domain-a.test" ]] \
  && ok "sender shown as $ALICE@domain-a.test" || fail "wrong sender on domain-b"

BODY=$(printf '{"to":["%s@domain-a.test"],"subject":"E2E reply %s","body":"Hello back from B"}' "$ALICE" "$SUFFIX")
expect "carol@b replies to alice@a" 201 "$(call carol POST "$B_URL/api/v1/mails" "$BODY")" carol
R_ID="$(json "$WORK/carol.out" 'd["data"]["id"]')"
ST="$(await_delivery carol "$B_URL" "$R_ID" "$ALICE@domain-a.test")"
[[ "$ST" == "DELIVERED" ]] && ok "B->A delivery status DELIVERED" || fail "B->A delivery status: $ST"
[[ -n "$(find_inbox alice "$A_URL" "E2E reply $SUFFIX")" ]] && ok "alice received the reply on domain-a" \
  || fail "reply missing in alice's inbox"

BODY=$(printf '{"to":["ghost%s@domain-b.test"],"subject":"E2E ghost %s","body":"x"}' "$SUFFIX" "$SUFFIX")
expect "send to unknown remote user accepted for delivery" 201 "$(call alice POST "$A_URL/api/v1/mails" "$BODY")" alice
G_ID="$(json "$WORK/alice.out" 'd["data"]["id"]')"
ST="$(await_delivery alice "$A_URL" "$G_ID" "ghost$SUFFIX@domain-b.test")"
[[ "$ST" == "FAILED" ]] && ok "unknown remote user ends FAILED (peer rejected it)" || fail "ghost delivery status: $ST"

echo "== Security checks"
expect "bob cannot read alice's sent copy" 404 "$(call bob GET "$A_URL/api/v1/mails/$SENT_ID")" bob
BODY='{"to":["someone@domain-z.test"],"subject":"x","body":"x"}'
expect "unconfigured domain rejected" 400 "$(call alice POST "$A_URL/api/v1/mails" "$BODY")" alice
[[ "$(json "$WORK/alice.out" 'd["error"]["details"][0]["reason"]')" == "DOMAIN_NOT_SUPPORTED" ]] \
  && ok "reason DOMAIN_NOT_SUPPORTED" || fail "unexpected rejection reason"
expect "unsigned federation request rejected" 401 "$(curl -s ${CURL_TLS[@]+"${CURL_TLS[@]}"} -o "$WORK/x.out" -w '%{http_code}' \
  -X POST -H 'Content-Type: application/json' --data '{}' "$B_URL/federation/v1/messages")"
# Fresh client on domain-a (carol's domain-b session/token are meaningless there).
refresh_csrf carol_on_a "$A_URL"
BODY=$(printf '{"username":"%s","password":"%s"}' "$CAROL" "$PASSWORD")
expect "carol (domain-b) cannot log in on domain-a" 401 "$(call carol_on_a POST "$A_URL/api/v1/auth/login" "$BODY")" carol_on_a
expect "request without CSRF token rejected" 403 "$(curl -s ${CURL_TLS[@]+"${CURL_TLS[@]}"} -o "$WORK/x.out" -w '%{http_code}' \
  -b "$WORK/alice.jar" -X POST -H 'Content-Type: application/json' --data '{}' "$A_URL/api/v1/drafts")"
refresh_csrf mallory "$A_URL"
BODY=$(printf '{"username":"%s","password":"WrongPassw0rd"}' "$BOB")
expect "wrong password rejected" 401 "$(call mallory POST "$A_URL/api/v1/auth/login" "$BODY")" mallory

echo "== Logout"
expect "alice logs out" 200 "$(call alice POST "$A_URL/api/v1/auth/logout")" alice
expect "alice's session is gone" 401 "$(call alice GET "$A_URL/api/v1/auth/me")" alice

echo
echo "Result: $PASS passed, $FAIL failed"
[[ $FAIL -eq 0 ]]
