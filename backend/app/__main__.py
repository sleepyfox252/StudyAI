"""Run the backend with configuration from the environment.

Usage (from the ``backend/`` directory, inside the virtualenv)::

    python -m app
"""

from __future__ import annotations

import uvicorn

from app.config import get_settings


def main() -> None:
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        log_level=settings.log_level,
        reload=settings.dev_mode,
    )


if __name__ == "__main__":
    main()
