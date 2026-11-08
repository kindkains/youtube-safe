# youtube-safe — Design-Spezifikation

**Stand:** 2026-11-08 · **Status:** vom Auftraggeber abschnittsweise freigegeben, Review des Gesamtdokuments ausstehend
**Arbeitstitel:** `youtube-safe` (nur privat; vor einer Veröffentlichung umbenennen, kein „YouTube“ im Namen)

## 1. Ziel und Rahmen

Ein YouTube-Client für Kinder, der standardmäßig **alles sperrt**, was die Eltern nicht ausdrücklich
freigegeben haben (Allowlist). Das Kind kann per Knopfdruck um Freigabe bitten, die Eltern entscheiden
in einer eigenen App. Dazu Verlauf, Schauzeit-Auswertung und ein zeitlich begrenzter Belohnungsmodus.

- Nutzer: eine Familie, mehrere Kinder (anfangs die Tochter, 11), drei gleichberechtigte Elternteile,
  alle mit Android.
- Erst **rein privat**. Spätere Veröffentlichung (GitHub, F-Droid) ist denkbar, prägt aber Version 1
  nur insofern, als nichts verbaut wird (FOSS-Abhängigkeiten, kein Google-Dienstzwang).
- Variante **A**: ohne Google-Konto. Variante B (echtes Google-Konto) ist ausdrücklich spätere Version.

### Nicht in Version 1

Google-Login · Tageslimits (macht weiter Kisi) · SMS/WhatsApp/Telegram/Signal · Downloads ·
Multi-Familien-Betrieb auf einem Server · Web-Elternportal · iOS.

### Rechtliche Leitplanken (für eine spätere Veröffentlichung)

- Nutzungsbedingungen: Vertrag zwischen YouTube und Zuschauer; kein Google-Konto im Spiel.
- § 95a UrhG: OLG Hamburg, 21.11.2024, 5 U 54/23 (youtube-dl/Uberspace) wertet YouTubes Rolling Cipher
  als wirksame technische Schutzmaßnahme. Konsequenz: keine Download-Funktion, kein Markenname,
  vor Veröffentlichung anwaltliche Prüfung.

## 2. Architektur

| Teil | Technik | Ort |
|---|---|---|
| Server | Python, FastAPI, SQLAlchemy, Alembic, MariaDB | Docker auf dem vServer (isoletta.me) hinter NPM |
| Push | ntfy (selbst gehostet, eigener Container), private Topics mit Token-Auth | vServer |
| E-Mail | SMTP über Mailcow | vServer |
| Kinder-App | Kotlin, Jetpack Compose, Media3, NewPipeExtractor | Android (Kinderhandy) |
| Eltern-App | Kotlin, Jetpack Compose, Media3, NewPipeExtractor | Android (Elternhandys) |
| Build/CI | GitHub Actions, privates Repo `kindkains/youtube-safe` | GitHub |

Grundsatzentscheidung: **eigene App auf Basis der Bibliothek NewPipeExtractor** (GPL-3), kein Fork der
NewPipe-/Tubular-App. Begründung: ein zentraler Filterpunkt statt Dutzender Einstiegspunkte; Updates
bedeuten nur eine neue Bibliotheksversion statt Merge-Konflikten.

Eine Server-Installation gehört genau einer Familie.

### Repo-Struktur

```
youtube-safe/
├── server/            FastAPI-App, Alembic, Tests, Dockerfile
├── android/
│   ├── core/          Server-Client, ntfy-Empfang, Player, Extractor-Wrapper, Self-Update, Entscheidungslogik
│   ├── kid/           Kinder-App
│   └── parent/        Eltern-App
├── shared/
│   └── gate-cases.json  gemeinsame Testtabelle der Entscheidungslogik (Server + App)
├── deploy/            docker-compose.yml, .env.example, NPM-Hinweise
└── docs/
```

## 3. Datenmodell (Server)

| Tabelle | Felder (Auszug) |
|---|---|
| `parent` | id, name, email, password_hash, ntfy_topic, mail_prefs (welche Mails), created_at |
| `child` | id, name, display_mode (`compact`/`full`), shorts_enabled (bool, Standard false), reward_until (UTC, nullable), reward_includes_blocked (bool), ntfy_topic |
| `device` | id, child_id, token_hash, app_version, last_seen, paired_at, unpaired_at |
| `pairing_code` | code_hash, child_id, expires_at (10 min), used_at |
| `rule` | id, child_id (NULL = alle Kinder), target_type (`channel`/`video`), target_id (YouTube-ID), title, channel_name, effect (`allow`/`block`), note, created_by, created_at, updated_at |
| `request` | id, child_id, target_type, target_id, title, channel_id, channel_name, url, thumbnail_url, child_message, status (`open`/`approved`/`rejected`), decided_by, decided_at, reason |
| `watch_session` | id, child_id, video_id, channel_id, title, channel_name, started_at, played_seconds, in_reward_mode (bool) |
| `device_event` | id, device_id, kind (`extractor_error`/`unpaired`/…), detail, created_at |

Eindeutigkeit: höchstens eine Regel pro (child_id, target_type, target_id). Die `request`-Tabelle ist
die History; eine erneute Entscheidung über eine alte Anfrage legt die zugehörige Regel an oder ändert sie.

## 4. Entscheidungslogik („ContentGate“)

Eingabe: Kind, Video-ID, Kanal-ID, aktuelle Serverzeit. Die **erste zutreffende Stufe gewinnt**:

1. Belohnungsmodus aktiv **und** `reward_includes_blocked` → **erlaubt**
2. Video-Regel `block` → **gesperrt**
3. Video-Regel `allow` → **erlaubt**
4. Kanal-Regel `block` → **gesperrt**
5. Kanal-Regel `allow` → **erlaubt** (gilt auch für künftige Videos)
6. Belohnungsmodus aktiv → **erlaubt**
7. sonst → **gesperrt** (mit „Freigabe erbitten“)

Innerhalb einer Stufe schlägt eine Regel mit `child_id` des Kindes eine Regel mit `child_id = NULL`.
„Belohnungsmodus aktiv“ heißt: `reward_until` liegt nach der aktuellen Serverzeit.

Implementiert zweimal (Python im Server, Kotlin in `core`); beide Testsuiten laufen gegen
`shared/gate-cases.json`.

## 5. Server-Schnittstelle

Alle Pfade unter `/api/v1`. Authentifizierung per Bearer-Token; Eltern- und Kindertoken sind getrennte
Rollen, ein Kindertoken erreicht nur `/kid/*`.

**Eltern** (`/parent/*`): Login; Kinder anlegen/bearbeiten; Kopplungscode erzeugen; Geräte entkoppeln;
Regeln CRUD; Anfragen listen (Filter Kind/Status/Zeitraum) und entscheiden; Verlauf; Statistik
(pro Tag/Woche, Top-Kanäle, Anteil Belohnungsmodus); Belohnungsmodus starten (Dauer oder Endzeit,
Schalter „auch Gesperrtes“) und beenden; Eltern anlegen; Mail-Einstellungen.

**Kind** (`/kid/*`): Kopplung mit Code; Regeln und Einstellungen abrufen (inkl. Serverzeit); Anfrage
stellen; eigene Anfragen; Schauzeit-Zwischenstände melden (gebündelt); Störung melden; Update-Info.

**Öffentlich**: `/updates/` (Versions-JSON und signierte APKs), Healthcheck.

Entscheidet ein Elternteil eine Anfrage, gilt sie für alle als erledigt; die anderen Eltern erhalten eine
Info-Push („Anna hat *XY* freigegeben“).

## 6. Kinder-App

### Bildschirme

- **Start**: neue Videos der freigegebenen Kanäle (neueste zuerst) plus freigegebene Einzelvideos;
  darunter „Meine Kanäle“.
- **Suche**: mit Vorschlägen; Ergebnisse durch das Gate.
- **Kanal**: freigegeben → Videos; sonst Kanalkopf + „Kanal-Freigabe erbitten“.
- **Video**: Player mit SponsorBlock, Return-YouTube-Dislike-Zahl, Kommentaren (bei freigegebenen Videos
  immer sichtbar), ähnliche Videos durch das Gate. Hintergrundwiedergabe und Popup-Player.
- **Gesperrt-Hinweis**: „Noch nicht freigegeben“ + [Dieses Video erbitten] + [Ganzen Kanal erbitten],
  optionales Nachrichtenfeld.
- **Meine Anfragen**: Status und ggf. Ablehnungsgrund.
- **Belohnungsmodus-Banner** mit Countdown.

### Darstellung gesperrter Inhalte (Eltern-Einstellung pro Kind)

- `compact` (Standard): in Suche und Vorschlägen nur Titel, Kanal, 🔒, „Freigabe erbitten“ — **kein
  Vorschaubild**.
- `full`: Vorschaubild wie bei YouTube, 🔒, nur Abspielen gesperrt.
- Startseite zeigt in beiden Modi ausschließlich Freigegebenes.

### Shorts

Standardmäßig aus (kein Shorts-Bereich, keine Shorts in Suche/Listen). Eingeschaltet laufen sie durch das
Gate und werden als **normale Videos ohne Endlos-Wischen** abgespielt. Erkennung über `/shorts/`-URL bzw.
Kennzeichnung durch den Extractor.

### Gate-Durchsetzung

- Jede Liste (Start, Suche, Kanal, ähnliche Videos) klassifiziert ihre Einträge über `ContentGate.classify()`.
- Der Player startet ausschließlich über `ContentGate.authorizePlayback(videoId)`, das die Kanal-ID aus den
  **echten Streamdaten** erneut prüft.
- Die App registriert sich für YouTube-Links (`youtube.com`, `youtu.be`, `m.youtube.com`); geteilte Links
  laufen durch dasselbe Gate.

### Schauzeit

Gezählt wird nur, solange der Player tatsächlich abspielt (`isPlaying`), inklusive Hintergrund und Popup;
Pause, Buffering und Stillstand nach Videoende zählen nicht. Zwischenstand alle 30 s an den Server,
offline lokal gepuffert (Room) und nachgereicht.

### Abgleich

Regeln und Einstellungen beim Start, nach jeder Push-Nachricht „Regeln geändert“ und alle 15 Minuten.
Push an das Kind: freigegeben, abgelehnt (mit Grund), Belohnungsmodus aktiv/beendet.

### Extras

Werbefrei (Extractor), SponsorBlock (API, immer an), Return YouTube Dislike (API, an),
Hintergrund- und Popup-Wiedergabe (an, zählen als Schauzeit). **Keine Downloads.**

### Manipulationsschutz

- Keine Einstellungen, kein Abmelden, kein Entkoppeln in der Kinder-App.
- App-Daten löschen = Gerät entkoppelt → App zeigt nur „Bitte von Mama oder Papa koppeln lassen“,
  Eltern erhalten Push „Gerät meldet sich nicht mehr“, sobald ein gekoppeltes Gerät 24 h lang keinen
  Abgleich gemacht hat (`last_seen`; das gelöschte Token kann sich nicht mehr melden). Eine Neukopplung
  desselben Kindes erzeugt die Info „neues Gerät gekoppelt“.
- Voraussetzung im Betrieb: Kisi sperrt die offizielle YouTube-App, NewPipe, Tubular u. ä. und YouTube im
  Browser, **ohne** die YouTube-Domains auf dem Kinderhandy zu sperren (unsere App nutzt dieselben Server).

## 7. Eltern-App

- **Übersicht**: Karte pro Kind (Schauzeit heute, offene Anfragen, Belohnungsmodus).
- **Anfragen**: offen oben, darunter History; Filter Kind/Status/Zeitraum.
- **Anfrage-Detail**: Vorschaubild, Titel, Kanal, Nachricht des Kindes, Link; bei Kanal-Anfragen die letzten
  10 Videos des Kanals. Eingebauter Vorschau-Player (ohne Filter) und [In YouTube öffnen].
  Aktionen: Freigeben (dieses Kind / alle Kinder) · bei Kanal-Anfragen „nur dieses Video“ · Ablehnen mit
  optionaler Begründung · Ablehnen + Kanal sperren.
- **Regeln**: Liste pro Kind bzw. „alle Kinder“, Suche, Bearbeiten, Löschen; neue Regel per eingefügtem
  Link oder In-App-Suche.
- **Verlauf**: Zeitleiste pro Kind; je Eintrag Video/Kanal freigeben oder sperren.
- **Statistik**: pro Tag/Woche, Top-Kanäle, Anteil Belohnungsmodus.
- **Belohnungsmodus**: Dauer (Tage/Stunden/Minuten) oder Endzeit, Schalter „auch Gesperrtes“, [Sofort beenden].
- **Kind-Einstellungen**: Anzeige `compact`/`full`, Shorts an/aus, Kopplungs-QR-Code, Geräte mit
  App-Version, Entkoppeln.
- **Eltern**: weitere gleichberechtigte Elternteile anlegen; eigene Mail-Einstellungen.

### Benachrichtigungen

| Ereignis | Push | E-Mail |
|---|---|---|
| Neue Anfrage | ja, mit Aktionen [Freigeben] [Ablehnen] | ja (Titel, Kanal, Vorschaubild, Link, Kind) |
| Anderes Elternteil hat entschieden | ja (Info) | nein |
| Belohnungsmodus abgelaufen | ja | nein |
| Gerät meldet sich 24 h nicht / App gestört | ja | ja |

Mails enthalten **keine** Ein-Klick-Freigabelinks. Die Push-Aktion [Freigeben] gibt für das anfragende
Kind frei (nicht für alle).

## 8. Push über ntfy

- Eigener ntfy-Container; jedes Elternteil und jedes Kind hat ein privates Topic mit Zugriffstoken.
- Der Server veröffentlicht per HTTP an ntfy; die Apps halten über einen Foreground-Service eine
  Dauerverbindung (ntfy-JSON-Stream bzw. WebSocket) zu ihrem Topic.
- Die App bittet beim ersten Start um Ausnahme von der Akku-Optimierung. Die vorgeschriebene ständige
  Benachrichtigung („verbunden“) bleibt, der Kanal lässt sich in Android ausblenden.
- Push-Inhalte enthalten nur IDs und kurze Texte; Details holt die App vom Server.

## 9. Updates

- **CI-Pipeline (GitHub Actions)**: bei jedem Push Tests; täglich Prüfung auf neue NewPipeExtractor-Version →
  Version anheben → Live-Selbsttest (Suche, Kanal, Stream-URL, Kommentare eines festen Testvideos) →
  bei Erfolg APKs bauen, signieren, auf `/updates/` hochladen; bei Fehlschlag Mail an die Eltern, alte
  Version bleibt.
- **Self-Update der Apps**: Abfrage der Versions-JSON, Download, Signaturprüfung, Installation über
  `PackageInstaller` mit `USER_ACTION_NOT_REQUIRED` (Android ≥ 12, App ist ihr eigener Installer;
  einmalig „Unbekannte Apps installieren“ erlauben).
- **Signierschlüssel**: einmalig erzeugt, als GitHub-Secret und als Backup auf der NAS.
- **Störungsfrühwarnung**: häufen sich Extractor-Fehler, meldet die Kinder-App das an den Server → Push und
  Mail an die Eltern; das Kind sieht „YouTube zickt gerade, wird repariert“.

## 10. Fehlerbehandlung

| Fall | Verhalten |
|---|---|
| Server nicht erreichbar | Kinder-App nutzt zuletzt geladene Regeln; Schauzeit und Anfragen werden gepuffert; Belohnungsmodus endet pünktlich nach lokal fortgeschriebener Serverzeit (Offset beim letzten Abgleich + monotone Uhr). |
| Regeln nie geladen | alles gesperrt (Fail-closed). |
| Extractor-Fehler | Hinweis im Kind, Störungsmeldung, Pipeline zieht Update. |
| Push kommt nicht an | 15-Minuten-Abgleich; offene Anfragen bleiben in der Eltern-App sichtbar. |
| Mail-Versand scheitert | Log + Wiederholung nach 5/15/60 min; Push unabhängig davon. |
| Kopplungscode abgelaufen/benutzt | klare Fehlermeldung, neuer Code aus der Eltern-App. |

## 11. Betrieb

- `deploy/docker-compose.yml`: `app`, `db`, `ntfy`; Konfiguration über `.env` (Pflichtwerte per `:?`-Guard).
- NPM-Host z. B. `tube.isoletta.me` mit Let's Encrypt; Pfade für API, ntfy und `/updates/`.
- Tägliches DB-Dump; Einbindung in den bestehenden Patchday.
- Bootstrap: erster Elternteil aus `.env`, weitere legt die Eltern-App an. Keine Selbstregistrierung.

## 12. Tests

- Entscheidungslogik: Tabellentests aus `shared/gate-cases.json`, in Python und Kotlin.
- Server: pytest gegen SQLite in-memory (Muster wie openVerba), inkl. Rollen-Trennung Eltern/Kind,
  Belohnungsmodus-Ablauf, Anfrage-Workflow, Statistik.
- Apps: Unit-Tests für Gate, Schauzeit-Zähler, Belohnungs-Countdown, Self-Update-Signaturprüfung;
  UI-Tests der Hauptabläufe im Emulator (CI).
- Live-Selbsttest gegen YouTube nur in der CI-Pipeline.

## 13. Fahrplan

| Version | Inhalt |
|---|---|
| 1.0 | dieses Dokument |
| später | Variante B (Google-Konto) · Tageslimits mit „+15 min erbitten“ · weitere Kanäle (SMS, WhatsApp, Telegram, Signal) · eigener Name, öffentliches Repo, F-Droid |
