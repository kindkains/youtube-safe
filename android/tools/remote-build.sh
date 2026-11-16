#!/usr/bin/env bash
# Baut bzw. testet die Android-Module auf einem x86-Rechner mit Android-SDK.
# Auf dem Pi geht das nicht: aapt2 gibt es von Google nur für x86.
#
# Aufruf aus beliebigem Verzeichnis:
#   android/tools/remote-build.sh :parent:testDebugUnitTest assembleDebug
# Voreinstellungen per Umgebung änderbar (REMOTE, REMOTE_USER, REMOTE_DIR, SDK_DIR).
set -euo pipefail

REMOTE="${REMOTE:-thinkbook}"
REMOTE_USER="${REMOTE_USER:-sandro}"
REMOTE_DIR="${REMOTE_DIR:-/home/sandro/youtube-safe/repo}"
SDK_DIR="${SDK_DIR:-/home/sandro/youtube-safe/android-sdk}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [ "$#" -eq 0 ]; then
    echo "Aufruf: $0 <gradle-Aufgaben …>" >&2
    exit 2
fi

ssh "$REMOTE" "mkdir -p '$REMOTE_DIR'"
# build/ und .gradle/ bleiben drüben erhalten (schnellere Folge-Builds), local.properties schreiben wir selbst.
rsync -a --delete \
    --exclude 'build/' --exclude '.gradle/' --exclude '.kotlin/' --exclude 'local.properties' \
    "$ROOT/android" "$ROOT/shared" "$REMOTE:$REMOTE_DIR/"

ssh "$REMOTE" "set -e
    printf 'sdk.dir=%s\n' '$SDK_DIR' > '$REMOTE_DIR/android/local.properties'
    chown -R '$REMOTE_USER': '$REMOTE_DIR'
    su - '$REMOTE_USER' -c \"cd '$REMOTE_DIR/android' && ./gradlew --console=plain $*\""
