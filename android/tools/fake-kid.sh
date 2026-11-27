#!/usr/bin/env bash
# Spielt ein Kinderhandy, um die Eltern-App zu testen (bis die Kinder-App aus Plan 3 da ist).
#
#   fake-kid.sh pair <Kopplungscode>                     koppeln, Token wird gespeichert
#   fake-kid.sh request-video <Video-ID> [Nachricht]     Video-Anfrage stellen
#   fake-kid.sh request-channel <Kanal-ID> [Video-ID]    Kanal-Anfrage (optional mit Ursprungsvideo)
#   fake-kid.sh watch <Video-ID> <Kanal-ID> <Sekunden>   Schauzeit melden
#
# SERVER=https://… überschreibt den Standard-Server.
set -euo pipefail

SERVER="${SERVER:-https://safetube.isoletta.me}"
STATE="${XDG_CONFIG_HOME:-$HOME/.config}/safetube-fake-kid-token"
TOKEN="$( [ -f "$STATE" ] && cat "$STATE" || true )"

api() {
    local method=$1 path=$2 body=${3:-}
    local args=(-fsS -X "$method" "$SERVER/api/v1$path" -H "Content-Type: application/json")
    if [ -n "$TOKEN" ]; then args+=(-H "Authorization: Bearer $TOKEN"); fi
    if [ -n "$body" ]; then args+=(--data "$body"); fi
    curl "${args[@]}"
}

json() { python3 -c 'import json,sys; print(json.dumps(dict(zip(sys.argv[1::2], sys.argv[2::2]))))' "$@"; }

case "${1:-}" in
    pair)
        TOKEN=""
        body=$(json code "${2:?Kopplungscode fehlt}" app_version fake-kid)
        mkdir -p "$(dirname "$STATE")"
        api POST /kid/pair "$body" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["token"]); print("gekoppelt mit", d["child_name"], file=sys.stderr)' > "$STATE"
        chmod 600 "$STATE"
        ;;
    request-video)
        body=$(json target_type video target_id "${2:?Video-ID fehlt}" title "Testvideo (fake-kid)" message "${3:-}")
        api POST /kid/requests "$body"; echo
        ;;
    request-channel)
        body=$(json target_type channel target_id "${2:?Kanal-ID fehlt}" title "Testkanal (fake-kid)" \
            source_video_id "${3:-}" source_video_title "${3:+Ursprungsvideo (fake-kid)}")
        api POST /kid/requests "$body"; echo
        ;;
    watch)
        body=$(python3 -c '
import json, sys, uuid, datetime
video, channel, seconds = sys.argv[1], sys.argv[2], int(sys.argv[3])
start = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
print(json.dumps({"entries": [{"client_id": uuid.uuid4().hex, "video_id": video, "channel_id": channel,
    "title": "Testvideo (fake-kid)", "channel_name": "Testkanal", "started_at": start, "played_seconds": seconds}]}))
' "${2:?Video-ID fehlt}" "${3:?Kanal-ID fehlt}" "${4:?Sekunden fehlen}")
        api POST /kid/watch "$body"; echo "gemeldet"
        ;;
    *)
        sed -n '2,10p' "$0"
        exit 2
        ;;
esac
