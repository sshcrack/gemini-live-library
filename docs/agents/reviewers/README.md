# Review subagents

Use these prompts to fan one Gemini Live Library change out to independent review-only agents. The prompts deliberately keep the code-review skill's **Spec** and **Standards** axes separate, then add domain lenses for failure modes that deserve their own context.

## Parent-agent workflow

1. Pick one fixed point and verify it resolves: `git rev-parse <FIXED_POINT>`.
2. Record `git diff <FIXED_POINT>...HEAD` and `git log <FIXED_POINT>..HEAD --oneline` once. Every reviewer must use that same three-dot comparison.
3. Give every reviewer `COMMON.md` plus exactly one specialist prompt below. Replace `<FIXED_POINT>` before dispatch.
4. Run `01` and `02` as the canonical Spec and Standards axes. Add the domain reviewers relevant to the changed files; for broad release reviews, run all of them.
5. Let `08-tests-build-and-release.md` own expensive Gradle/build/release validation. Other reviewers may inspect existing tests and build files, but should not start broad builds unless a focused reproduction is essential to prove a finding.
6. Aggregate reports by lens. Deduplicate identical root causes, but do not let a clean domain review cancel a Spec or Standards finding.

## Specialist prompts

- `01-spec-and-scope.md` — requested behavior, missing requirements, wrong behavior, scope creep.
- `02-standards-and-architecture.md` — repository conventions, maintainability, module boundaries, Fowler smell baseline.
- `03-public-api-and-compatibility.md` — public Java surface, source/binary compatibility, exceptions, callback contracts.
- `04-live-protocol-and-session.md` — Gemini Live wire semantics, setup, events, tool calls, interruption, resumption, model/config correctness.
- `05-websocket-transport-and-concurrency.md` — low-level WebSocket/TLS correctness, thread safety, close/reconnect behavior, resource ownership.
- `06-flash-tts-and-streaming.md` — GenerateContent/TTS HTTP semantics, retries, streaming parsers, audio metadata, failure propagation.
- `07-serialization-and-schema.md` — Gson wire shape, property/schema classes, tool declarations/responses, null/enum/array/object encoding.
- `08-tests-build-and-release.md` — regression coverage, Stonecutter/loader builds, publication artifacts, versions/tags/changelog/release workflow.

Each reviewer reports findings only. Fixes belong to a later implementation pass so reviewers stay independent.
