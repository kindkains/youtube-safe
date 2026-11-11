# Deployment

1. `cp .env.example .env`, Werte füllen (Passwörter: `openssl rand -base64 24`).
2. `mkdir -p updates && docker compose --env-file .env up -d --build`
3. ntfy-Benutzer für den Server anlegen und Token erzeugen:
   ```bash
   docker compose exec ntfy ntfy user add --role=admin yts-server
   docker compose exec ntfy ntfy token add yts-server
   ```
   Token in `.env` als `NTFY_TOKEN` eintragen, dann `docker compose up -d app`.
4. NPM: `tube.isoletta.me` → `127.0.0.1:8090`, `push.tube.isoletta.me` → `127.0.0.1:8091`
   (Websockets an), jeweils mit Let's Encrypt.
5. Prüfen: `curl -fsS https://tube.isoletta.me/health` → `{"status":"ok"}`
6. Backup: `docker compose exec db mariadb-dump -u root -p"$MARIADB_ROOT_PASSWORD" youtube_safe > backup.sql`
