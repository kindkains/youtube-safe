"""Passwörter, Zufallstokens, Kopplungscodes und ntfy-Topics."""

from __future__ import annotations

import base64
import hashlib
import secrets

import bcrypt

# Ohne 0/O und 1/I, damit sich der Code notfalls auch abtippen lässt.
PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


def _prepare(password: str) -> bytes:
    """bcrypt verarbeitet nur 72 Byte; vorher hashen statt still kürzen."""
    digest = hashlib.sha256(password.encode("utf-8")).digest()
    return base64.b64encode(digest)


def hash_password(password: str) -> str:
    return bcrypt.hashpw(_prepare(password), bcrypt.gensalt()).decode("ascii")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return bcrypt.checkpw(_prepare(password), password_hash.encode("ascii"))
    except (ValueError, TypeError):
        return False


def new_token() -> str:
    return secrets.token_urlsafe(32)


def hash_token(token: str) -> str:
    """Tokens und Codes liegen nur als Hash in der Datenbank."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def new_pairing_code() -> str:
    return "".join(secrets.choice(PAIRING_ALPHABET) for _ in range(8))


def new_topic() -> str:
    return "yts-" + secrets.token_hex(16)
