"""Service layer.

Phase 3.1 introduced the AI service ports (``interfaces``) and their default,
unavailable implementations (``unavailable``). Phase 3.2 adds
:class:`~app.services.generation.ProviderGenerationService`, a real generation
implementation backed by a pluggable :mod:`app.ai` provider. The provider is
wired up at application startup (see ``app.main``); when none is configured the
unavailable implementations still answer, and tests substitute fakes via
``app.dependency_overrides``.
"""
