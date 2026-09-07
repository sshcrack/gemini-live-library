# Prompt: Gemini Live protocol and session reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` for correctness against the Gemini Live API contract.

Read the repo skill `gemini-live-api-dev`. Verify version-sensitive protocol claims against current official Google Gemini API documentation when required by that skill.

## Review for

- Incorrect Live endpoint/model identifiers or setup message shape.
- Setup-complete gating/order errors and messages incorrectly ignored before/after setup.
- Real-time input encoded through the wrong message kind or wrong audio/text/video field.
- Server events handled as mutually exclusive when one event may legally carry multiple meaningful fields/parts.
- Model-turn loops that drop later parts, audio chunks, text, transcriptions, usage metadata, or other callbacks due to an early `return`.
- Fragile MIME/sample-rate parsing or incorrect assumptions about Live input/output PCM format.
- Function-call parsing/response IDs/names/arguments/results that do not match the wire contract; synchronous tool-call flow that can strand the model.
- Interruption, generation-complete, turn-complete, GoAway, session-resumption, or resumable-handle behavior that loses state or produces duplicate/late callbacks.
- Translation configuration/model semantics that differ from the upstream Live Translate contract.
- Unsupported/deprecated configuration being presented as valid for the selected model.
- Quota/error detection based on brittle strings when structured protocol/close information is available.

Every finding must name the upstream rule being violated and connect it to the exact local code path. Do not report missing optional Gemini features unless the spec or public API claims to support them.
