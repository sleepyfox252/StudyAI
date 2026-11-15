"""Tests for AI provider configuration parsing and validation."""

from __future__ import annotations

from pathlib import Path

import pytest

from app.config import ConfigurationError, OpenCodeSettings, load_settings

NO_ENV_FILE = Path("/tmp/studyai-nonexistent.env")


def test_opencode_defaults() -> None:
    settings = load_settings(env={}, env_file=NO_ENV_FILE)

    assert settings.ai_provider == "opencode"
    assert settings.opencode == OpenCodeSettings()
    assert settings.opencode.base_url == "http://127.0.0.1:4096"
    assert settings.opencode.timeout_seconds == 120.0
    assert settings.opencode.max_retries == 2
    assert settings.opencode.username is None
    assert settings.opencode.password is None


def test_prefixed_and_bare_env_names_are_accepted() -> None:
    prefixed = load_settings(
        env={"STUDYAI_OPENCODE_BASE_URL": "http://127.0.0.1:5000"},
        env_file=NO_ENV_FILE,
    )
    bare = load_settings(env={"OPENCODE_BASE_URL": "http://localhost:5000"}, env_file=NO_ENV_FILE)

    assert prefixed.opencode.base_url == "http://127.0.0.1:5000"
    assert bare.opencode.base_url == "http://localhost:5000"


def test_prefixed_name_wins_over_bare_name() -> None:
    settings = load_settings(
        env={
            "STUDYAI_OPENCODE_BASE_URL": "http://127.0.0.1:1111",
            "OPENCODE_BASE_URL": "http://127.0.0.1:2222",
        },
        env_file=NO_ENV_FILE,
    )
    assert settings.opencode.base_url == "http://127.0.0.1:1111"


def test_ai_provider_and_retries_are_configurable() -> None:
    settings = load_settings(
        env={
            "STUDYAI_AI_PROVIDER": "none",
            "STUDYAI_OPENCODE_MAX_RETRIES": "0",
            "STUDYAI_OPENCODE_TIMEOUT_SECONDS": "30",
            "STUDYAI_OPENCODE_SERVER_USERNAME": "user",
            "STUDYAI_OPENCODE_SERVER_PASSWORD": "pass",
        },
        env_file=NO_ENV_FILE,
    )

    assert settings.ai_provider == "none"
    assert settings.opencode.max_retries == 0
    assert settings.opencode.timeout_seconds == 30.0
    assert settings.opencode.username == "user"
    assert settings.opencode.password == "pass"


@pytest.mark.parametrize(
    "url",
    ["http://8.8.8.8:4096", "https://1.1.1.1:4096"],
)
def test_public_ip_base_url_is_rejected(url: str) -> None:
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_BASE_URL": url}, env_file=NO_ENV_FILE)


@pytest.mark.parametrize(
    "url",
    ["http://127.0.0.1:4096", "http://192.168.1.10:4096", "http://10.0.0.5:4096"],
)
def test_private_and_loopback_base_urls_are_accepted(url: str) -> None:
    settings = load_settings(env={"STUDYAI_OPENCODE_BASE_URL": url}, env_file=NO_ENV_FILE)
    assert settings.opencode.base_url == url


def test_non_http_scheme_is_rejected() -> None:
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_BASE_URL": "ftp://127.0.0.1"}, env_file=NO_ENV_FILE)


def test_out_of_range_timeout_is_rejected() -> None:
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_TIMEOUT_SECONDS": "0"}, env_file=NO_ENV_FILE)
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_TIMEOUT_SECONDS": "9999"}, env_file=NO_ENV_FILE)


def test_out_of_range_retries_is_rejected() -> None:
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_MAX_RETRIES": "-1"}, env_file=NO_ENV_FILE)
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_MAX_RETRIES": "99"}, env_file=NO_ENV_FILE)


def test_non_numeric_timeout_is_rejected() -> None:
    with pytest.raises(ConfigurationError):
        load_settings(env={"STUDYAI_OPENCODE_TIMEOUT_SECONDS": "soon"}, env_file=NO_ENV_FILE)
