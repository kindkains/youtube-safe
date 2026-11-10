"""Konfiguration aus der Umgebung.

Kein anderes Modul liest direkt aus os.environ.
"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # --- Anwendung ---
    base_url: str = Field(default="http://localhost:8080", alias="YTS_BASE_URL")
    timezone: str = Field(default="Europe/Berlin", alias="YTS_TIMEZONE")
    jobs_enabled: bool = Field(default=True, alias="YTS_JOBS_ENABLED")
    updates_dir: str = Field(default="/srv/updates", alias="YTS_UPDATES_DIR")

    # --- Erster Elternteil (nur, solange es noch keinen gibt) ---
    bootstrap_parent_name: str = Field(default="", alias="YTS_PARENT_NAME")
    bootstrap_parent_email: str = Field(default="", alias="YTS_PARENT_EMAIL")
    bootstrap_parent_password: str = Field(default="", alias="YTS_PARENT_PASSWORD")

    # --- Datenbank ---
    db_host: str = Field(default="db", alias="DB_HOST")
    db_port: int = Field(default=3306, alias="DB_PORT")
    db_name: str = Field(default="youtube_safe", alias="MARIADB_DATABASE")
    db_user: str = Field(default="youtube_safe", alias="MARIADB_USER")
    db_password: str = Field(default="", alias="MARIADB_PASSWORD")
    # Vollständige URL; überschreibt die Einzelwerte (Tests laufen gegen SQLite).
    database_url_override: str = Field(default="", alias="DATABASE_URL")

    # --- Mail (optional) ---
    smtp_host: str = Field(default="", alias="SMTP_HOST")
    smtp_port: int = Field(default=587, alias="SMTP_PORT")
    smtp_user: str = Field(default="", alias="SMTP_USER")
    smtp_password: str = Field(default="", alias="SMTP_PASSWORD")
    smtp_from: str = Field(default="", alias="SMTP_FROM")
    smtp_starttls: bool = Field(default=True, alias="SMTP_STARTTLS")

    # --- Push über ntfy (optional) ---
    # ntfy_url: wohin der Server veröffentlicht (intern, z. B. http://ntfy)
    # ntfy_public_url: womit sich die Apps verbinden (über NPM, https)
    ntfy_url: str = Field(default="", alias="NTFY_URL")
    ntfy_public_url: str = Field(default="", alias="NTFY_PUBLIC_URL")
    ntfy_token: str = Field(default="", alias="NTFY_TOKEN")

    @field_validator("base_url", "ntfy_url", "ntfy_public_url")
    @classmethod
    def _strip_trailing_slash(cls, value: str) -> str:
        return value.rstrip("/")

    @property
    def database_url(self) -> str:
        if self.database_url_override:
            return self.database_url_override
        return (
            f"mysql+pymysql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}?charset=utf8mb4"
        )

    @property
    def mail_enabled(self) -> bool:
        return bool(self.smtp_host)

    @property
    def push_enabled(self) -> bool:
        return bool(self.ntfy_url)


@lru_cache
def get_settings() -> Settings:
    return Settings()
