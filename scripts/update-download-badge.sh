#!/usr/bin/env bash
set -euo pipefail

repo="sksense/karoo-WaterMelonControl"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/badges/downloads.json"

total="$({
  gh api "repos/$repo/releases" --paginate \
    --jq '.[] | .assets[] | select(.name | test("\\.apk$"; "i")) | .download_count'
} | awk '{sum += $1} END {print sum + 0}')"

mkdir -p "$(dirname "$out")"
cat > "$out" <<JSON
{
  "schemaVersion": 1,
  "label": "downloads",
  "message": "$total",
  "color": "brightgreen"
}
JSON

echo "Updated $out with total APK downloads: $total"
