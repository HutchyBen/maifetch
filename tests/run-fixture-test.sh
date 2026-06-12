#!/usr/bin/env sh
# AI-assisted with OpenAI GPT-5 Codex.
set -eu

bin="${1:-build/maifetch}"
root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

output="$(
  MAIFETCH_PROFILE_FIXTURE="$root/tests/profile.json" \
  MAIFETCH_PLAYS_FIXTURE="$root/tests/plays.json" \
  "$bin" --access-token fixture-token --logo-size 0 --score-count 2
)"

printf '%s\n' "$output"

printf '%s\n' "$output" | grep -F "MAI" >/dev/null
printf '%s\n' "$output" | grep -F "ID: 42" >/dev/null
printf '%s\n' "$output" | grep -F "Rating: 12.34 / 15.00" >/dev/null
printf '%s\n' "$output" | grep -F "Level: 17" >/dev/null
printf '%s\n' "$output" | grep -F "Total Credits: 99" >/dev/null
printf '%s\n' "$output" | grep -F "Test Song  Master" >/dev/null
printf '%s\n' "$output" | grep -F "1,000,000 100.5000% SSS+ FC" >/dev/null
printf '%s\n' "$output" | grep -F "Second Song  Expert" >/dev/null

if "$bin" --logo-size 0 >/tmp/maifetch-missing-token.out 2>&1; then
  echo "expected missing-token run to fail" >&2
  exit 1
fi
grep -F "access token is required" /tmp/maifetch-missing-token.out >/dev/null

echo "fixture tests passed"
