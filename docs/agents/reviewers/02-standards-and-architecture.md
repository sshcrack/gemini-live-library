# Prompt: Standards and architecture reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` through **Standards** only.

Inspect repository guidance such as `AGENTS.md`, `.editorconfig`, build conventions, comments/docs that declare invariants, and established local patterns. Repository rules override generic preferences.

## Architecture checks

Look for changed code that makes responsibilities harder to locate or reason about: protocol parsing mixed with policy, transport ownership leaking into callers, duplicated wire-shape logic, lifecycle state spread across unrelated types, abstractions with no current need, or public types that expose mutable implementation details.

Apply this Fowler smell baseline as judgement-call heuristics, not hard rules:

- **Mysterious Name** — names hide purpose or state.
- **Duplicated Code** — the same logic shape is repeated.
- **Feature Envy** — logic works mostly on another object's data.
- **Data Clumps** — the same values travel together and want a type.
- **Primitive Obsession** — strings/primitives stand in for a domain concept.
- **Repeated Switches** — repeated branching on the same conceptual type/state.
- **Shotgun Surgery** — one logical change requires scattered edits.
- **Divergent Change** — one module changes for unrelated reasons.
- **Speculative Generality** — abstractions/hooks exist without a present requirement.
- **Message Chains** — callers navigate internals they should not know.
- **Middle Man** — a type mostly delegates without owning a useful abstraction.
- **Refused Bequest** — inheritance exposes behavior an implementation does not meaningfully support.

For documented-standard breaches, cite the repository rule. For smells, label them as judgement calls and explain the concrete maintenance consequence in this diff. Skip anything already enforced mechanically by tooling.
