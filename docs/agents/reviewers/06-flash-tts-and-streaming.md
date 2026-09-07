# Prompt: Flash, TTS, and HTTP streaming reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` for the non-WebSocket Gemini helpers: `GeminiFlash`, `GeminiTTS`, related exceptions, and their HTTP/streaming behavior.

Use current official Gemini documentation for version-sensitive request/response facts.

## Review for

- GenerateContent or streamGenerateContent request fields serialized with the wrong JSON names/nesting/types.
- Response parsing that assumes fields/parts are always present, mistakes valid API responses for failures, or silently accepts malformed responses.
- HTTP status handling that loses status code/body, tries to parse error bodies as success JSON, or treats permanent failures as retryable.
- Retry policy bugs: retrying unsafe/non-retryable conditions, failing to retry intended transient failures, overflow/unbounded backoff, swallowed interruption, or duplicated expensive requests after partial success.
- TTS stream parsing errors across JSON arrays, sequential objects, truncated streams, or mixed text/audio parts.
- Audio decoding/sample-rate handling that emits corrupt chunks or ambiguous invalid sample rates.
- InputStream/HttpClient/resource ownership leaks and exceptions from consumers that bypass cleanup.
- Blocking semantics or callback behavior that changed without being reflected in the public contract.
- Structured-output configuration that uses an incorrect schema field or returns text in a way callers cannot reliably interpret.

Separate upstream API correctness from library ergonomics. Only report the latter when it creates a concrete caller failure or compatibility problem.
