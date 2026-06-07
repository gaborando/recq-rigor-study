#!/usr/bin/env bash
# Fetch the pinned static-analysis tools (CK, PMD, cloc) and verify checksums
# against config/versions.lock.yaml.
#
#   ./tools/fetch_tools.sh                # fetch + verify
#   ./tools/fetch_tools.sh --update-lock  # fetch + record sha256 into the lock file
#
# Versions/URLs live ONLY in config/versions.lock.yaml.

set -euo pipefail
cd "$(dirname "$0")/.."

LOCK=config/versions.lock.yaml
UPDATE_LOCK="${1:-}"

need() { command -v "$1" >/dev/null || { echo "missing: $1" >&2; exit 1; }; }
need curl; need uv; need shasum

yq() { # tiny yaml getter: yq <dotted.path>
  uv run --quiet python - "$1" <<'EOF'
import sys, yaml
path = sys.argv[1].split('.')
with open('config/versions.lock.yaml') as f:
    node = yaml.safe_load(f)
for p in path:
    node = node[p]
print(node if node is not None else "")
EOF
}

fetch() { # fetch <name> <url> <dest> <lockpath-for-sha>
  local name="$1" url="$2" dest="$3" lockpath="$4"
  if [[ -z "$url" || "$url" == "None" || "$url" == "null" ]]; then
    echo "SKIP $name: url not pinned in $LOCK yet" >&2
    return 0
  fi
  echo "fetching $name ..."
  curl -fsSL "$url" -o "$dest"
  local sha; sha=$(shasum -a 256 "$dest" | cut -d' ' -f1)
  local expected; expected=$(yq "$lockpath")
  if [[ "$UPDATE_LOCK" == "--update-lock" ]]; then
    uv run --quiet python - "$lockpath" "$sha" <<'EOF'
import sys, yaml
path, sha = sys.argv[1].split('.'), sys.argv[2]
with open('config/versions.lock.yaml') as f:
    data = yaml.safe_load(f)
node = data
for p in path[:-1]:
    node = node[p]
node[path[-1]] = sha
with open('config/versions.lock.yaml', 'w') as f:
    yaml.safe_dump(data, f, sort_keys=False)
EOF
    echo "  recorded sha256 $sha"
  elif [[ "$expected" == "RECORD_ON_FIRST_FETCH" ]]; then
    echo "  WARNING: no checksum recorded yet; run with --update-lock" >&2
  elif [[ "$sha" != "$expected" ]]; then
    echo "  CHECKSUM MISMATCH for $name: got $sha want $expected" >&2
    exit 1
  else
    echo "  checksum OK"
  fi
}

fetch ck "$(yq static_tools.ck.url)" tools/ck.jar static_tools.ck.sha256

# PMD and cloc URLs are resolved/pinned at first use (see lock file).
fetch pmd  "$(yq static_tools.pmd.url)"  tools/pmd.zip  static_tools.pmd.sha256
fetch cloc "$(yq static_tools.cloc.url)" tools/cloc     static_tools.cloc.sha256

if [[ -f tools/pmd.zip ]]; then
  unzip -qo tools/pmd.zip -d tools/ && rm tools/pmd.zip
fi
if [[ -f tools/cloc ]]; then chmod +x tools/cloc; fi

echo "done."
