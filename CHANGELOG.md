# Changelog

## [2.4.0] - 2026-09-07

### Added

- Opt-in structured-output configuration for `GeminiFlash` GenerateContent requests while preserving the existing text-only overloads.
- `GeminiLiveTranslateClient` and Live translation generation configuration.
- HTTP status and raw provider-response access on `UnexpectedResponseException` for non-2xx Gemini TTS responses.

### Fixed

- Tolerate Live function calls that omit an `id` instead of failing while constructing the function response.
- Surface non-2xx Gemini TTS responses as `UnexpectedResponseException` before attempting to parse them as streamed audio.

This release is additive relative to 2.3.4: existing public constructors and method signatures remain available, so these changes do not require a 3.x breaking-version bump.
