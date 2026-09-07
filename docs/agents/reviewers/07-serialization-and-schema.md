# Prompt: Serialization and schema reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` for JSON/wire-model correctness.

Focus on `gson/**`, property/schema classes, Live setup/input/tool response structures, and any explicit Gson serialization choices in the helpers.

## Review for

- Java field names that serialize to the wrong upstream key, including mixed camelCase/snake_case and missing `@SerializedName` where required.
- Serialization performed against the wrong declared class/type so subclass/runtime fields disappear or unintended fields leak.
- Null/default handling that emits invalid wire values or omits required values.
- Enum/property schemas whose allowed values, type tags, array items, object properties, required fields, descriptions, or nesting do not match the intended declaration.
- Mutable lists/maps shared across requests or schema instances in a way callers can corrupt later payloads.
- Tool declarations and tool responses whose IDs/names/arguments/results cannot round-trip through the Live protocol.
- Numeric/boolean/string coercion that makes a schema claim one type while runtime values use another.
- Generic/raw types that let invalid schemas compile and fail only at runtime.
- Tests that compare object state but never assert the actual serialized JSON wire shape.

For each finding, show the expected JSON fragment and the actual fragment or serialization path that produces the mismatch. Do not report naming aesthetics that do not affect serialized output or public API clarity.
