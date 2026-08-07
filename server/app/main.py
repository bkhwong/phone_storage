import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .config import Settings, get_settings
from .db import init_db
from .routes import assets, health, pair, uploads

logger = logging.getLogger(__name__)


def _warn_if_using_default_pin(settings: Settings) -> None:
    if settings.pair_pin == Settings.model_fields["pair_pin"].default:
        logger.warning(
            "PAIR_PIN is unset and using the built-in default (%r). Anyone who can reach "
            "this server on the network can pair a device with it. Set PAIR_PIN in "
            "server/.env before exposing this beyond a trusted LAN.",
            settings.pair_pin,
        )


@asynccontextmanager
async def lifespan(_app: FastAPI):
    settings = get_settings()
    settings.ensure_dirs()
    _warn_if_using_default_pin(settings)
    init_db()
    yield


app = FastAPI(
    title="Custom Photo Sync Server",
    version="0.1.0",
    lifespan=lifespan,
)

# LAN phone clients authenticate via an X-Device-Token header, not cookies, so
# allow_credentials must be False — it's also an invalid/unsafe combination with
# a wildcard origin (browsers reject it outright). Origins stay permissive since
# this is a LAN-only server with no cookie-based session to leak.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(pair.router)
app.include_router(assets.router)
app.include_router(uploads.router)


def run() -> None:
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        reload=False,
    )


if __name__ == "__main__":
    run()
