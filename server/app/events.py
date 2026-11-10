"""Fachliche Benachrichtigungen: wer bekommt bei welchem Ereignis was.

Push-Tags, auf die die Apps reagieren:
- "sync"            Kind: Regeln/Einstellungen neu laden (ohne sichtbare Meldung)
- "request:<id>"    Eltern: neue Anfrage (App baut daraus die Knöpfe Freigeben/Ablehnen)
- "decision"        Kind: Entscheidung über eine Anfrage
- "reward"          Kind: Belohnungsmodus gestartet/beendet
- "info"            Eltern: reine Information
- "alert"           Eltern: Störung, Gerät meldet sich nicht

Mails werden nur in die Outbox gelegt; Commit macht der Aufrufer.
"""

from __future__ import annotations

from sqlalchemy import select
from sqlalchemy.orm import Session

from app import notify
from app.models import Child, ContentRequest, Device, Parent

KIND_LABEL = {"video": "Video", "channel": "Kanal"}


def _parents(session: Session) -> list[Parent]:
    return list(session.scalars(select(Parent).order_by(Parent.id)))


def sync_child(child: Child) -> None:
    notify.send_push(child.ntfy_topic, "sync", "sync", tags=("sync",), priority=1)


def sync_all_children(session: Session) -> None:
    for child in session.scalars(select(Child)):
        sync_child(child)


def request_created(session: Session, request: ContentRequest, child: Child) -> None:
    label = KIND_LABEL[request.target_type]
    title = f"{child.name} bittet um Freigabe"
    message = f"{label}: {request.title or request.target_id}"
    if request.channel_name and request.target_type == "video":
        message += f" ({request.channel_name})"
    if request.child_message:
        message += f"\n„{request.child_message}“"
    for parent in _parents(session):
        notify.send_push(parent.ntfy_topic, title, message, tags=(f"request:{request.id}",), priority=4)
        if parent.mail_new_request:
            notify.queue_mail(
                session,
                parent.email,
                f"youtube-safe: {title}",
                f"{message}\n\nLink: {request.url}\n"
                + (f"Vorschaubild: {request.thumbnail_url}\n" if request.thumbnail_url else "")
                + "\nEntscheiden kannst du in der Eltern-App.\n",
            )


def decision_made(
    session: Session, request: ContentRequest, child: Child, by: Parent, *, all_children: bool
) -> None:
    approved = request.status == "approved"
    label = KIND_LABEL[request.target_type]
    name = request.title or request.target_id
    if approved:
        kid_title = "Freigegeben 🎉"
        kid_message = f"{label}: {name}"
    else:
        kid_title = "Leider abgelehnt"
        kid_message = f"{label}: {name}" + (f"\nGrund: {request.reason}" if request.reason else "")
    notify.send_push(child.ntfy_topic, kid_title, kid_message, tags=("decision",))
    if all_children:
        sync_all_children(session)
    else:
        sync_child(child)

    verb = "freigegeben" if approved else "abgelehnt"
    for parent in _parents(session):
        if parent.id == by.id:
            continue
        notify.send_push(
            parent.ntfy_topic,
            f"{by.name} hat {verb}",
            f"{child.name}: {label} {name}",
            tags=("info", f"decided:{request.id}"),
            priority=2,
        )


def rules_changed(session: Session, child_id: int | None) -> None:
    if child_id is None:
        sync_all_children(session)
    else:
        child = session.get(Child, child_id)
        if child is not None:
            sync_child(child)


def reward_started(child: Child, until_text: str) -> None:
    notify.send_push(child.ntfy_topic, "Belohnungsmodus aktiv 🎉", f"Alles frei bis {until_text}",
                     tags=("reward", "sync"))


def reward_stopped(child: Child) -> None:
    notify.send_push(child.ntfy_topic, "Belohnungsmodus beendet", "Ab jetzt gelten wieder die normalen Regeln.",
                     tags=("reward", "sync"))


def reward_expired(session: Session, child: Child) -> None:
    reward_stopped(child)
    for parent in _parents(session):
        notify.send_push(parent.ntfy_topic, "Belohnungsmodus abgelaufen", child.name,
                         tags=("info",), priority=2)


def device_paired(session: Session, child: Child) -> None:
    for parent in _parents(session):
        notify.send_push(parent.ntfy_topic, "Neues Gerät gekoppelt", child.name,
                         tags=("info",), priority=2)


def _alert(session: Session, title: str, message: str) -> None:
    for parent in _parents(session):
        notify.send_push(parent.ntfy_topic, title, message, tags=("alert",), priority=4)
        if parent.mail_alerts:
            notify.queue_mail(session, parent.email, f"youtube-safe: {title}", message + "\n")


def device_stale(session: Session, device: Device, child: Child) -> None:
    _alert(
        session,
        "Gerät meldet sich nicht mehr",
        f"Das Gerät von {child.name} hat sich seit über 24 Stunden nicht gemeldet. "
        "Wurden die App-Daten gelöscht oder die App deinstalliert?",
    )


def disturbance(session: Session, child: Child, detail: str) -> None:
    _alert(
        session,
        "App gestört",
        f"Die App von {child.name} meldet Probleme beim Laden von YouTube. "
        f"Ein Update ist vermutlich unterwegs.\n\nDetail: {detail[:500]}",
    )
