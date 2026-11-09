# youtube-safe — Projektanweisungen

Kinderschutz-YouTube-Client für Android: Alles ist gesperrt, was die Eltern nicht ausdrücklich
freigegeben haben. Das Kind kann per Knopf um Freigabe bitten, die Eltern entscheiden in einer eigenen
App. Dazu Verlauf, Schauzeit und ein immer befristeter Belohnungsmodus.

Homelab-spezifisches (Hosts, Deployment, Fallstricke, offene Punkte) steht in `CLAUDE.local.md`
(nicht im Repo):

@CLAUDE.local.md

## Maßgebliche Dokumente

| Dokument | Inhalt |
|---|---|
| `docs/superpowers/specs/2026-11-08-youtube-safe-design.md` | **Spec**: von Jessica abschnittsweise freigegeben, verbindlich |
| `docs/superpowers/plans/2026-11-08-server.md` | **Plan 1 von 4 (Server)**: 17 Tasks, TDD, noch nicht begonnen |

Pläne 2–4 sind noch zu schreiben (writing-plans-Skill), jeweils erst wenn der vorige umgesetzt ist:
2. Android `core` + Eltern-App · 3. Kinder-App · 4. CI-Release-Pipeline (täglicher NewPipeExtractor-Check,
Live-Selbsttest, Signieren, `/updates/`) und Self-Update der Apps.

## Architektur in einem Absatz

Server: FastAPI + SQLAlchemy 2 + Alembic + MariaDB in Docker, dazu ein ntfy-Container für Push; Stil und
Aufbau wie `/home/claude/claude.ai/openVerba` (dort abschauen: config.py, db.py, conftest.py, Dockerfile).
Zwei Android-Apps (Kotlin, Jetpack Compose, Media3) mit gemeinsamem Modul `core`; die Kinder-App nutzt die
Bibliothek **NewPipeExtractor** direkt (eigene Oberfläche, **kein** Fork der NewPipe-/Tubular-App). Jeder
Inhalt läuft durch genau eine Entscheidungsfunktion (`ContentGate`), die es identisch in Python
(`server/app/gate.py`) und Kotlin gibt; beide werden gegen `shared/gate-cases.json` getestet.

## Feste Entscheidungen von Jessica (nicht eigenmächtig umkippen)

- **Variante A**: ohne Google-Konto. Variante B (echtes Google-Konto) erst in einer späteren Version.
- **Zwei getrennte Apps** (Kind / Eltern), keine gemeinsame App mit Elternmodus. Alle Eltern haben Android.
- **Push per ntfy, selbst gehostet, direkt in beide Apps eingebaut** — keine separate ntfy-App, kein Firebase.
- **Mehrere Kinder mit eigenen Regeln**; Freigabe wahlweise „dieses Kind“ oder „alle Kinder“; alle Eltern
  gleichberechtigt, wer zuerst entscheidet, erledigt es für alle.
- Gesperrte Inhalte in Suche/Vorschlägen: Standard **kompakt** (kein Vorschaubild), per Eltern-Schalter
  **vollständig**. Startseite zeigt immer nur Freigegebenes.
- **Shorts aus** per Standard; eingeschaltet laufen sie als normale Videos **ohne Endlos-Wischen**.
- **Kommentare** bei freigegebenen Videos immer sichtbar.
- **Schauzeit nur messen**, keine Limits (macht weiter Kisi). Nur echtes Abspielen zählt.
- Extras: werbefrei, SponsorBlock, Return YouTube Dislike, Hintergrund- und Popup-Wiedergabe an;
  **keine Downloads** (Zeitmessung und § 95a UrhG).
- **Belohnungsmodus immer zeitlich begrenzt**, Ablauf nach Serveruhr; optional „auch Gesperrtes“.
- **Build/CI über GitHub Actions** (privates Repo); Apps aktualisieren sich selbst (Android ≥ 12,
  `USER_ACTION_NOT_REQUIRED`).
- Erst **rein privat**. Bei späterer Veröffentlichung (GitHub public, F-Droid): neuer Name ohne
  „YouTube“, anwaltliche Prüfung wegen OLG Hamburg 21.11.2024, 5 U 54/23 (Rolling Cipher = wirksame
  Schutzmaßnahme nach § 95a UrhG).
- Lizenz **GPL-3.0** (Pflicht wegen NewPipeExtractor).

## Arbeitsweise in diesem Repo

- Plan-Tasks strikt in Reihenfolge, TDD wie im Plan (Test zuerst, scheitern sehen, implementieren,
  grün, committen). Nach jedem Task-Block kurzer Zwischenstand an Jessica.
- Server-Tests: `cd server && .venv/bin/python -m pytest` (venv laut Plan Task 0 anlegen).
- Commits mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; lokal ohne globale
  git-Identität, daher `git -c user.name="Claude" -c user.email="noreply@anthropic.com" commit ...`.
- Remote `origin` = `git@github.com:kindkains/youtube-safe.git` (privat, Branch `main`). Kein `gh`-CLI
  auf dem Pi — Repo-Einstellungen, Secrets und Actions-Ergebnisse muss Jessica im Browser erledigen
  bzw. nachsehen.
- Code, Kommentare und Commit-Texte auf **Deutsch**, Stil wie openVerba.
- Erkenntnisse, die nicht im Code stehen, laufend in `CLAUDE.local.md` unter „Laufende Notizen“ eintragen
  (Jessica will regelmäßige Zwischenstände gegen Datenverlust).
