"""Centralized, environment-based configuration for the StudyAI backend.

Configuration is read from environment variables (optionally seeded from a local
``.env`` file next to this package). Real environment variables always take
precedence over ``.env`` values so a deployment can override anything. No
credentials or secrets are hardcoded; only non-secret operational settings live
here.

Settings are exposed through :func:`get_settings`, which is cached for the
process. Call :func:`load_settings` directly to build a fresh instance (used by
tests) or to validate an explicit mapping of environment variables.
"""

from __future__ import annotations

import ipaddress
import os
from functools import lru_cache
from pathlib import Path
from typing import Mapping
from urllib.parse import urlsplit

from pydantic import BaseModel, ConfigDict, ValidationError, field_validator

# --- Static identity ---------------------------------------------------------------

SERVICE_NAME = "studyai-backend"
BACKEND_VERSION = "0.1.0"
API_VERSION = "v1"

# --- Environment handling ----------------------------------------------------------

_ENV_PREFIX = "STUDYAI_"
_BACKEND_DIR = Path(__file__).resolve().parent.parent
_DEFAULT_ENV_FILE = _BACKEND_DIR / ".env"

_TRUTHY = {"1", "true", "yes", "on"}
_FALSY = {"0", "false", "no", "off"}


class ConfigurationError(RuntimeError):
    """Raised when environment configuration is malformed or missing.

    Carries a human-readable message that identifies the offending variable so
    startup failures are easy to diagnose.
    """


class Limits(BaseModel):
    """Configurable resource limits that protect the service from abuse.

    All values are positive integers and can be overridden through the
    environment (see :func:`load_settings`). Nothing is silently truncated;
    violating a limit produces an explicit error response instead.
    """

    model_config = ConfigDict(frozen=True, extra="forbid")

    # Maximum accepted request body size, in bytes.
    max_request_bytes: int = 1_048_576  # 1 MiB
    # Maximum length of the ``topic`` field, in characters.
    max_topic_length: int = 500
    # Maximum length of the ``additionalInstructions`` field, in characters.
    max_instruction_length: int = 2_000
    # Maximum length of the ``question`` field, in characters.
    max_question_length: int = 2_000
    # Maximum serialized size of an embedded visualization document, in bytes.
    max_document_bytes: int = 524_288  # 512 KiB
    # Maximum length of the ``context`` (explanation context) field, in characters.
    max_explanation_context_length: int = 4_000

    @field_validator("*")
    @classmethod
    def _positive(cls, value: int) -> int:
        if value <= 0:
            raise ValueError("must be a positive integer")
        return value


# Bounds for the OpenCode client. The timeout is bounded so a stuck upstream
# can never hold a request (or a worker) open indefinitely; retries are bounded
# so a failing upstream cannot amplify load without limit.
_MIN_TIMEOUT_SECONDS = 1.0
_MAX_TIMEOUT_SECONDS = 600.0
_MAX_MAX_RETRIES = 10


def _validate_base_url(url: str) -> str:
    """Validate an OpenCode base URL and enforce a local-only posture.

    The OpenCode server must never be reachable from outside the host, so a
    literal IP address must be loopback or private. Hostnames (for example
    ``localhost``) are accepted and resolved at request time.
    """

    candidate = url.strip()
    parts = urlsplit(candidate)
    if parts.scheme not in {"http", "https"}:
        raise ValueError("must be an http(s) URL")
    if not parts.hostname:
        raise ValueError("must include a host")
    try:
        address = ipaddress.ip_address(parts.hostname)
    except ValueError:
        return candidate
    if not (address.is_loopback or address.is_private):
        raise ValueError(
            "must point to a loopback or private address; OpenCode must not be exposed publicly"
        )
    return candidate


class OpenCodeSettings(BaseModel):
    """Configuration for the local OpenCode server used as the AI provider.

    Credentials are optional and only used when the server is configured with
    authentication. They are never logged and never included in responses.
    """

    model_config = ConfigDict(frozen=True, extra="forbid")

    base_url: str = "http://127.0.0.1:4096"
    timeout_seconds: float = 120.0
    max_retries: int = 2
    username: str | None = None
    password: str | None = None

    @field_validator("base_url")
    @classmethod
    def _check_base_url(cls, value: str) -> str:
        return _validate_base_url(value)

    @field_validator("timeout_seconds")
    @classmethod
    def _check_timeout(cls, value: float) -> float:
        if not _MIN_TIMEOUT_SECONDS <= value <= _MAX_TIMEOUT_SECONDS:
            raise ValueError(
                f"must be between {_MIN_TIMEOUT_SECONDS} and {_MAX_TIMEOUT_SECONDS} seconds"
            )
        return value

    @field_validator("max_retries")
    @classmethod
    def _check_max_retries(cls, value: int) -> int:
        if not 0 <= value <= _MAX_MAX_RETRIES:
            raise ValueError(f"must be between 0 and {_MAX_MAX_RETRIES}")
        return value


class Settings(BaseModel):
    """Validated runtime settings for the backend."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    host: str = "127.0.0.1"
    port: int = 8000
    dev_mode: bool = False
    log_level: str = "info"
    limits: Limits = Limits()
    # Which AI provider backs generation. ``"opencode"`` enables the local
    # OpenCode adapter; any other value disables generation (503).
    ai_provider: str = "opencode"
    opencode: OpenCodeSettings = OpenCodeSettings()

    @field_validator("ai_provider")
    @classmethod
    def _valid_ai_provider(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value.strip()

    @field_validator("host", "log_level")
    @classmethod
    def _not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value

    @field_validator("port")
    @classmethod
    def _valid_port(cls, value: int) -> int:
        if not 1 <= value <= 65535:
            raise ValueError("must be between 1 and 65535")
        return value


def _parse_bool(raw: str, variable: str) -> bool:
    normalized = raw.strip().lower()
    if normalized in _TRUTHY:
        return True
    if normalized in _FALSY:
        return False
    raise ConfigurationError(
        f"{variable} must be a boolean (one of {sorted(_TRUTHY | _FALSY)}), got {raw!r}"
    )


def _parse_int(raw: str | None, variable: str, default: int) -> int:
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ConfigurationError(f"{variable} must be an integer, got {raw!r}") from exc


def _parse_float(raw: str | None, variable: str, default: float) -> float:
    if raw is None or not raw.strip():
        return default
    try:
        return float(raw)
    except ValueError as exc:
        raise ConfigurationError(f"{variable} must be a number, got {raw!r}") from exc


def _read_env_file(path: Path) -> dict[str, str]:
    """Parse a minimal ``KEY=VALUE`` dotenv file.

    Blank lines and lines starting with ``#`` are ignored. Values may be
    optionally wrapped in single or double quotes. This deliberately supports
    only the subset needed for local development, avoiding an extra dependency.
    """

    if not path.is_file():
        return {}

    values: dict[str, str] = {}
    for lineno, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ConfigurationError(f"{path}:{lineno}: expected KEY=VALUE, got {raw_line!r}")
        key, _, value = line.partition("=")
        key = key.strip()
        if not key:
            raise ConfigurationError(f"{path}:{lineno}: empty configuration key")
        values[key] = value.strip().strip('"').strip("'")
    return values


def load_settings(
    env: Mapping[str, str] | None = None,
    env_file: Path | None = None,
) -> Settings:
    """Build :class:`Settings` from ``env`` (defaults to ``os.environ``).

    A ``.env`` file (``env_file`` or ``backend/.env``) is used as a fallback;
    explicit environment variables always win. Raises :class:`ConfigurationError`
    with a clear message when a value is malformed.
    """

    source = dict(os.environ if env is None else env)
    dotenv = _read_env_file(env_file if env_file is not None else _DEFAULT_ENV_FILE)
    merged = {**dotenv, **source}

    def get(name: str) -> str | None:
        return merged.get(_ENV_PREFIX + name)

    def get_any(name: str) -> str | None:
        # Prefer the namespaced STUDYAI_-prefixed name, but accept the bare name
        # too so existing OpenCode tooling conventions keep working.
        return merged.get(_ENV_PREFIX + name) or merged.get(name)

    defaults = Limits()
    opencode_defaults = OpenCodeSettings()
    try:
        limits = Limits(
            max_request_bytes=_parse_int(
                get("MAX_REQUEST_BYTES"), _ENV_PREFIX + "MAX_REQUEST_BYTES", defaults.max_request_bytes
            ),
            max_topic_length=_parse_int(
                get("MAX_TOPIC_LENGTH"), _ENV_PREFIX + "MAX_TOPIC_LENGTH", defaults.max_topic_length
            ),
            max_instruction_length=_parse_int(
                get("MAX_INSTRUCTION_LENGTH"),
                _ENV_PREFIX + "MAX_INSTRUCTION_LENGTH",
                defaults.max_instruction_length,
            ),
            max_question_length=_parse_int(
                get("MAX_QUESTION_LENGTH"), _ENV_PREFIX + "MAX_QUESTION_LENGTH", defaults.max_question_length
            ),
            max_document_bytes=_parse_int(
                get("MAX_DOCUMENT_BYTES"), _ENV_PREFIX + "MAX_DOCUMENT_BYTES", defaults.max_document_bytes
            ),
            max_explanation_context_length=_parse_int(
                get("MAX_EXPLANATION_CONTEXT_LENGTH"),
                _ENV_PREFIX + "MAX_EXPLANATION_CONTEXT_LENGTH",
                defaults.max_explanation_context_length,
            ),
        )
        opencode = OpenCodeSettings(
            base_url=get_any("OPENCODE_BASE_URL") or opencode_defaults.base_url,
            timeout_seconds=_parse_float(
                get_any("OPENCODE_TIMEOUT_SECONDS"),
                _ENV_PREFIX + "OPENCODE_TIMEOUT_SECONDS",
                opencode_defaults.timeout_seconds,
            ),
            max_retries=_parse_int(
                get_any("OPENCODE_MAX_RETRIES"),
                _ENV_PREFIX + "OPENCODE_MAX_RETRIES",
                opencode_defaults.max_retries,
            ),
            username=get_any("OPENCODE_SERVER_USERNAME") or None,
            password=get_any("OPENCODE_SERVER_PASSWORD") or None,
        )
        return Settings(
            host=get("HOST") or "127.0.0.1",
            port=get("PORT") or "8000",
            dev_mode=_parse_bool(get("DEV_MODE") or "false", _ENV_PREFIX + "DEV_MODE"),
            log_level=get("LOG_LEVEL") or "info",
            limits=limits,
            ai_provider=get_any("AI_PROVIDER") or "opencode",
            opencode=opencode,
        )
    except ValidationError as exc:
        raise ConfigurationError(f"Invalid StudyAI backend configuration:\n{exc}") from exc


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Return the process-wide settings (cached)."""

    return load_settings()


def get_limits() -> Limits:
    """Return the configured resource limits."""

    return get_settings().limits
