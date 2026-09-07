# Prompt: Public API and compatibility reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` as a consumer of the published Java library.

Treat everything reachable from public/protected classes, methods, constructors, fields, nested types, callback hooks, checked exceptions, and published artifacts as compatibility surface unless the repository clearly marks it internal.

## Review for

- Source or binary breaks that are not reflected in the intended release/versioning decision.
- Signature changes that compile but silently change semantics, defaults, callback ordering, nullability, blocking behavior, retries, or ownership.
- New public mutable fields/types that expose wire/transport internals unnecessarily.
- Subclass hooks whose lifecycle/order can no longer be relied on, including `onOpen`, setup-complete, message callbacks, function calls, interruption/turn completion, close/error handling, and translation callbacks.
- Exception behavior changes: swallowed errors, new unchecked failures, lost status/body context, changed interruption behavior, or checked exceptions no longer matching actual failure modes.
- Public methods that accept invalid states without a clear contract, or reject previously valid inputs accidentally.
- Inconsistent API shape between related helpers (`GeminiLiveClient`, `GeminiLiveTranslateClient`, `GeminiFlash`, `GeminiTTS`, schema/property classes).
- Publication/source-set mistakes that mean consumers see a different API than local compilation/tests see.

Check call sites and tests where available. Do not argue for backward compatibility merely because a break exists: determine whether the chosen version/spec intends the break and report mismatches between actual compatibility impact and release intent.
