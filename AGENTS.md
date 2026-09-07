# Agent guidance — Gemini Live Library

## Review subagents

For branch, release, or fixed-point reviews that fan out across independent review-only agents, use `docs/agents/reviewers/README.md`. It defines one shared diff/evidence contract plus separate Spec, Standards, public API, Gemini Live protocol, WebSocket/concurrency, Flash/TTS, serialization/schema, and validation/release lenses.

## Repository basics

This is a Java Gemini API library packaged through Stonecutter for `1.20.1-forge` and `1.21.1-neoforge`. Treat `.sc_active_version` as generated/protected state. For broad Gradle validation in Laptop MCP, prefer `GRADLE_USER_HOME=/cache/gradle` and serialized workers (`--max-workers=1`) so parallel dependency work does not contend unnecessarily.

For current Gemini Live protocol semantics, read the repository skill `gemini-live-api-dev` and follow its official-documentation lookup guidance for version-sensitive facts.
