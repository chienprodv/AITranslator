#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
for p in Path('app/src').rglob('*.xml'):
    ET.parse(p)
print('XML OK')
PY
node --check backend/cloudflare-worker/worker.js
python3 - <<'PY'
from pathlib import Path
import re
s=Path('demo/index.html').read_text()
Path('/tmp/ngoctho-demo-inline.js').write_text('\n'.join(re.findall(r'<script[^>]*>(.*?)</script>', s, flags=re.S|re.I)))
PY
node --check /tmp/ngoctho-demo-inline.js
set +e
javac -proc:none $(find app/src/main/java -name '*.java' | sort) >/tmp/ngoctho-javac.txt 2>&1
set -e
if grep -E "';' expected|illegal start of|reached end of file while parsing|not a statement|class, interface, enum, or record expected|unclosed string literal|')' expected|']' expected" /tmp/ngoctho-javac.txt; then
  echo 'Java parser error'
  exit 1
fi
echo 'Java parser-level syntax OK (Android SDK classes are not available in this check).'
