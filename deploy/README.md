# Deployment

1. `cp .env.example .env`, Werte füllen (Passwörter: `openssl rand -base64 24`).
2. `mkdir -p updates && docker compose --env-file .env up -d --build`
3. ntfy-Benutzer für den Server anlegen und Token erzeugen:
   ```bash
   docker compose exec safetube-ntfy ntfy user add --role=admin yts-server
   docker compose exec safetube-ntfy ntfy token add yts-server
   ```
   Token in `.env` als `NTFY_TOKEN` eintragen, dann `docker compose up -d safetube-app`.
4. NPM (hängt an `YTS_PROXY_NETWORK`): `safetube.isoletta.me` → `safetube-app:8080`,
   `push.tube.isoletta.me` → `safetube-ntfy:80` (Websockets an), jeweils mit Let's Encrypt.
   Keine Port-Bindung auf dem Host: NPM läuft selbst im Container, `127.0.0.1` wäre dort NPM selbst.
5. Prüfen: `curl -fsS https://safetube.isoletta.me/health` → `{"status":"ok"}`
6. Backup: `docker compose exec db mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" youtube_safe > backup.sql`
