# StudyAI Backend

The backend service for StudyAI. This is an independent Python project that lives
inside the StudyAI repository alongside the Android app (`:app`) and the
pure-Kotlin engine (`:core`), but has no build-time dependency on either.

**Phase 3.2 scope:** OpenCode provider integration and the generation pipeline.
`POST /api/v1/generate` is now backed by a real provider (a local OpenCode
server) when one is configured and reachable; otherwise it honestly reports
itself as unavailable (`503`). Explanation and expansion remain unavailable
(Phase 3.3+). There is no streaming and no persistence.

The provider sits behind a narrow `TextGenerationProvider` interface, so the API
layer is unchanged from Phase 3.1 and the whole pipeline is testable without any
network access.

## Requirements

- Python 3.11+ (developed against 3.13)
- The packages in `pyproject.toml` (FastAPI, Uvicorn, Pydantic v2, httpx)

## Setup

```bash
cd backend

# 1. Create an isolated virtual environment
python3 -m venv .venv

# 2. Install runtime + test dependencies
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install fastapi uvicorn pydantic pytest httpx
#    (or, if you prefer: .venv/bin/python -m pip install -e ".[dev]")

# 3. Optional: create a local config file
cp .env.example .env
```

> On Debian/PRoot, use the system Python (`/usr/bin/python3`) for the venv: it
> targets glibc, so the prebuilt `pydantic-core` wheel installs without a Rust
> toolchain.

## Configuration

All configuration is environment-based (optionally seeded from `backend/.env`,
which is git-ignored). Real environment variables always win. No secrets are
stored in the repository.

| Variable                          | Default     | Meaning                                        |
| --------------------------------- | ----------- | ---------------------------------------------- |
| `STUDYAI_HOST`                    | `127.0.0.1` | Interface to bind                              |
| `STUDYAI_PORT`                    | `8000`      | TCP port (1–65535)                             |
| `STUDYAI_DEV_MODE`                | `false`     | Enables uvicorn reload + FastAPI debug         |
| `STUDYAI_LOG_LEVEL`               | `info`      | Uvicorn log level                              |
| `STUDYAI_MAX_REQUEST_BYTES`       | `1048576`   | Maximum request body size (bytes)              |
| `STUDYAI_MAX_TOPIC_LENGTH`        | `500`       | Maximum `topic` length (characters)            |
| `STUDYAI_MAX_INSTRUCTION_LENGTH`  | `2000`      | Maximum `additionalInstructions` length        |
| `STUDYAI_MAX_QUESTION_LENGTH`     | `2000`      | Maximum `question` length                      |
| `STUDYAI_MAX_DOCUMENT_BYTES`      | `524288`    | Maximum serialized document size (bytes)       |
| `STUDYAI_MAX_EXPLANATION_CONTEXT_LENGTH` | `4000` | Maximum `context` length                   |
| `STUDYAI_AI_PROVIDER`             | `opencode`  | AI provider backend (`opencode` or `none`)     |
| `STUDYAI_OPENCODE_BASE_URL`       | `http://127.0.0.1:4096` | OpenCode server URL (loopback/private only) |
| `STUDYAI_OPENCODE_TIMEOUT_SECONDS`| `120`       | Per-request timeout (1–600)                    |
| `STUDYAI_OPENCODE_MAX_RETRIES`    | `2`         | Retries after a failed attempt (0–10)          |
| `STUDYAI_OPENCODE_SERVER_USERNAME`| *(unset)*   | Optional HTTP Basic username                   |
| `STUDYAI_OPENCODE_SERVER_PASSWORD`| *(unset)*   | Optional HTTP Basic password                   |

The `OPENCODE_*` variables also work without the `STUDYAI_` prefix; the
namespaced name wins if both are set. `STUDYAI_OPENCODE_BASE_URL` must resolve to
a loopback or private address — a public IP is rejected at startup so the
OpenCode server can never be exposed.

Malformed values fail fast with a clear `ConfigurationError` at startup. Limits
are enforced explicitly — requests are never silently truncated.

## Running

From the `backend/` directory, with the virtualenv active (or via `.venv/bin/python`):

```bash
# Using the configured host/port/dev-mode from the environment:
python -m app

# Or run uvicorn directly:
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Interactive API docs are then available at `http://<host>:<port>/docs`.

## Testing

```bash
cd backend
python -m pytest
```

Tests do not require an AI provider or any API key: routes are exercised against
in-test fakes (`tests/fakes.py`) injected through `app.dependency_overrides`, and
the OpenCode adapter is tested against an `httpx.MockTransport`. No network
access is used by the suite.

## API

All AI endpoints live under `/api/v1`. Request and response fields are camelCase.

### `GET /health`

```json
{ "status": "ok", "apiVersion": "v1", "backendVersion": "0.1.0" }
```

### `GET /api/v1/capabilities`

```json
{
  "service": "studyai-backend",
  "apiVersion": "v1",
  "schemaVersions": ["1.0"],
  "operations": ["generate"],
  "sceneNodeTypes": ["group", "circle", "rect", "line", "text", "component"],
  "aiProviders": ["opencode"],
  "generationAvailable": true,
  "explanationAvailable": false,
  "expansionAvailable": false,
  "streaming": false
}
```

Generation is advertised only when a provider is configured **and** reports
itself healthy; otherwise `generationAvailable` is `false`, `aiProviders` is `[]`
and `operations` is `[]`. The endpoint never advertises a capability that would
immediately fail.

### `POST /api/v1/generate`

Generate a visualization document for a topic.

```jsonc
// request
{
  "topic": "Quadratic equations",      // required
  "subject": "mathematics",            // optional: mathematics|physics|chemistry|biology
  "locale": "en",                      // optional, default "en"
  "difficulty": 2,                     // optional, 1..5
  "additionalInstructions": "...",     // optional
  "context": "..."                     // optional
}

// 200 response
{ "document": { /* a full visualization document (schemaVersion "1.0") */ } }
```

### `POST /api/v1/explain`

Explain an element, an existing explanation or a free-form question, in the
context of a document. At least one of `elementId`, `explanationId` or
`question` is required.

```jsonc
// request
{
  "document": { /* visualization document */ },  // required
  "elementId": "root-a",                          // optional
  "explanationId": "exp_root_a",                  // optional
  "question": "Why two roots?",                   // optional
  "locale": "en"                                  // optional
}

// 200 response
{
  "explanation": "...",
  "keyPoints": ["..."],
  "relatedElementIds": ["..."],
  "relatedExplanationIds": ["..."]
}
```

### `POST /api/v1/expand`

Expand the explanation identified by `explanationId` within a document.

```jsonc
// request
{
  "document": { /* visualization document */ },  // required
  "explanationId": "exp_root_a",                  // required
  "locale": "en",                                 // optional
  "difficulty": 3                                 // optional, 1..5
}

// 200 response
{
  "title": "...",
  "description": "...",
  "document": { /* optional richer document */ },
  "relatedExplanations": [ /* optional Explanation objects */ ]
}
```

### Errors

Every error response uses one envelope:

```json
{ "error": { "code": "unsupported_subject", "message": "...", "details": { } } }
```

| Code                        | Status | Meaning                                        |
| --------------------------- | ------ | ---------------------------------------------- |
| `invalid_request`           | 400/422 | Malformed or invalid request                  |
| `unsupported_subject`       | 422    | `subject` is not a supported subject           |
| `unsupported_difficulty`    | 400/422 | Difficulty outside 1..5                        |
| `unsupported_schema_version`| 400    | Document uses an unsupported schema version    |
| `invalid_document`          | 400/422 | Document fails structural/semantic validation  |
| `missing_element`           | 404    | Referenced `elementId` not in the document     |
| `missing_explanation`       | 404    | Referenced `explanationId` not in the document |
| `request_too_large`         | 413    | Body or document exceeds its size limit        |
| `not_found`                 | 404    | Unknown route                                  |
| `generation_unavailable`    | 503    | No generation provider configured              |
| `explanation_unavailable`   | 503    | No explanation provider configured             |
| `expansion_unavailable`     | 503    | No expansion provider configured               |
| `provider_unavailable`      | 503    | Provider reachable but failed                  |
| `invalid_provider_response` | 503    | Provider returned an invalid document          |
| `provider_authentication_error` | 502 | Provider rejected the credentials           |
| `provider_timeout`          | 504    | Provider request exceeded the timeout          |
| `provider_rate_limited`     | 429    | Provider rate limited the request              |
| `provider_error`            | 502    | Provider returned an error response            |
| `generation_failed`         | 502    | All generation attempts produced invalid output |
| `internal_error`            | 500    | Unexpected server error                        |

The `code` is stable and machine-readable; branch on it rather than on
`message`. Documents produced by a provider are treated as untrusted and are
validated against the schema before being returned; a bad provider document is a
`503`, never a `200`.

## AI provider (OpenCode)

When `STUDYAI_AI_PROVIDER=opencode` (the default), the backend creates a shared
`httpx.AsyncClient` and an `OpenCodeProvider` at startup (the `app/main.py`
lifespan). The server is never launched automatically, and a missing or
unreachable server never prevents startup — `/capabilities` simply reports
generation unavailable.

The generation pipeline (`app/services/generation.py`) is:

1. `app/ai/prompts.py` builds the prompt (system rules + user turn);
2. the provider returns raw text;
3. the text is parsed (tolerating stray markdown fences, never repaired) and
   validated with the same rules applied to client documents;
4. on a retryable failure or invalid output the model is re-prompted with
   concise validation feedback, up to `STUDYAI_OPENCODE_MAX_RETRIES` times.

Transient failures (unreachable, timeout, rate limit, retryable request errors)
and invalid output are retried; authentication failures and permanent request
rejections fail immediately. A persistent transport problem keeps its own error
code, while repeated invalid output becomes `generation_failed`.

The installed OpenCode server exposes session-based generation (there is no
stateless text endpoint), so the adapter performs a one-shot generation through a
throwaway session: `POST /session`, one text part via
`POST /session/{id}/message`, then `DELETE /session/{id}`. Every OpenCode tool is
explicitly disabled on the message so a generation can never read, write or
execute anything on the host. Credentials are optional, sent only as HTTP Basic
auth, and never logged or returned to clients.

## Visualization schema

`app/models/visualization.py` mirrors the Kotlin schema in
`core/src/main/kotlin/com/studyai/core/visualization/model/` field-for-field,
including the polymorphic `"type"` discriminator for scene nodes and hit shapes.
Decoding is strict: unknown keys and unknown node types are rejected. Colors are
decoded permissively as strings so that semantic problems remain the validator's
responsibility. Semantic rules (schema version, difficulty range, unique ids,
reference resolution, size) are applied by `app/services/documents.py` and are
shared by every route that accepts or emits a document.

## Architecture

Routes depend on service interfaces (`app/services/interfaces.py`), never on a
concrete provider. The provider itself sits behind
`app/ai/base.py::TextGenerationProvider`, so a different backend can be added
without touching the API layer. When no provider is configured the default
(unavailable) implementations answer; tests inject fakes via
`app.dependency_overrides`. This keeps the HTTP layer independent of any AI
vendor and makes the backend fully testable without network access.

```
backend/
├── pyproject.toml
├── README.md
├── .env.example
├── app/
│   ├── main.py             # FastAPI app factory, lifespan, ASGI entry point
│   ├── __main__.py         # `python -m app`
│   ├── config.py           # environment-based configuration + limits + provider
│   ├── dependencies.py     # service/provider/limit dependency providers
│   ├── exceptions.py       # AppError hierarchy
│   ├── ai/
│   │   ├── base.py         # TextGenerationProvider port + typed errors
│   │   ├── prompts.py      # system + generation/correction prompts
│   │   └── providers/
│   │       └── opencode.py # local OpenCode server adapter
│   ├── api/
│   │   ├── errors.py       # centralized exception handlers
│   │   ├── middleware.py   # request body size limit (ASGI)
│   │   └── routes/
│   │       ├── health.py
│   │       ├── capabilities.py
│   │       ├── generation.py
│   │       ├── explanation.py
│   │       └── expansion.py
│   ├── models/
│   │   ├── common.py       # health/capabilities response models
│   │   ├── errors.py       # error envelope + codes
│   │   ├── requests.py     # AI request DTOs
│   │   ├── responses.py    # AI response DTOs
│   │   └── visualization.py  # Pydantic mirror of the Kotlin schema
│   └── services/
│       ├── context.py      # validated service inputs
│       ├── results.py      # service outputs
│       ├── interfaces.py   # service ports (ABCs)
│       ├── documents.py    # semantic document validation
│       ├── generation.py   # provider-backed generation pipeline
│       └── unavailable.py  # default (unavailable) services
└── tests/
    ├── fakes.py
    ├── test_health.py
    ├── test_capabilities.py
    ├── test_visualization_schema.py
    ├── test_ai_config.py
    ├── test_opencode_provider.py
    ├── test_generation_service.py
    ├── test_generation_api.py
    ├── test_generate.py
    ├── test_explain.py
    ├── test_expand.py
    └── test_errors.py
```
