from functools import lru_cache
from pathlib import Path
from typing import ClassVar

from pydantic_settings import BaseSettings, SettingsConfigDict

# server/ directory (parent of app/)
_SERVER_DIR = Path(__file__).resolve().parent.parent

# Common Android album locations mirrored under STORAGE_ROOT
PHONE_SEED_FOLDERS: tuple[str, ...] = (
    "DCIM/Camera",
    "Pictures/Screenshots",
    "Pictures",
    "Download",
    "Movies",
    "WhatsApp/Media/WhatsApp Images",
    "WhatsApp/Media/WhatsApp Video",
    "Other",
)


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_SERVER_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    storage_root: Path = _SERVER_DIR.parent / "storage"
    db_path: Path = _SERVER_DIR.parent / "data" / "photo_sync.db"
    pair_pin: str = "123456"
    host: str = "0.0.0.0"
    port: int = 8787
    pair_pin_reusable: bool = True
    version: str = "0.1.0"

    # Not loaded from env — kept on the class for callers that prefer Settings.*
    phone_seed_folders: ClassVar[tuple[str, ...]] = PHONE_SEED_FOLDERS

    def resolve_paths(self) -> None:
        """Make relative paths resolve against the server directory."""
        if not self.storage_root.is_absolute():
            self.storage_root = (_SERVER_DIR / self.storage_root).resolve()
        else:
            self.storage_root = self.storage_root.resolve()
        if not self.db_path.is_absolute():
            self.db_path = (_SERVER_DIR / self.db_path).resolve()
        else:
            self.db_path = self.db_path.resolve()

    def ensure_dirs(self) -> None:
        self.resolve_paths()
        self.storage_root.mkdir(parents=True, exist_ok=True)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        (self.storage_root / ".uploads").mkdir(parents=True, exist_ok=True)
        (self.storage_root / ".thumbs").mkdir(parents=True, exist_ok=True)
        for folder in PHONE_SEED_FOLDERS:
            (self.storage_root / folder).mkdir(parents=True, exist_ok=True)


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.ensure_dirs()
    return settings
