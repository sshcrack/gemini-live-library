# Prompt: Tests, build, and release reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` for verification and delivery correctness. This is the reviewer that may run the broad Gradle validation needed to substantiate findings.

## Test coverage review

Check that changed behavior has deterministic regression coverage at the right layer, especially:

- exact serialized request/wire payloads;
- multiple meaningful fields/parts in one Live server event;
- malformed/partial responses and HTTP failures;
- retry boundaries and interrupted retries;
- close/session/resource lifecycle;
- public compatibility behavior that callers depend on.

Flag tests that only exercise happy-path object construction while missing the failure mode introduced by the change.

## Build and packaging review

Inspect Stonecutter and both supported variants (`1.20.1-forge`, `1.21.1-neoforge`), Gradle source/dependency/publication wiring, artifact collection, and Maven publication. Verify the changed public classes/resources are present in the artifacts consumers receive.

Run the repository's relevant finite checks. Prefer serialized Gradle execution in this environment, e.g. `GRADLE_USER_HOME=/cache/gradle ./gradlew test buildAndCollect --no-daemon --max-workers=1`, when those tasks exist. Do not invent success if an external dependency/network restriction blocks a check; report the exact limitation.

## Release review

Check:

- `mod.version` / channel tag versus intended semantic compatibility impact;
- release tag validation and artifact path assumptions;
- Maven coordinates/publication tasks and repository configuration;
- changelog coverage of user-visible/breaking behavior;
- CI JDK/build parity where it can change results;
- accidental metadata drift (description, homepage, project IDs) exposed by the change.

A release/version finding needs a concrete mismatch: e.g. breaking public API under a non-breaking version, artifact not published, tag cannot satisfy workflow, or consumer-visible behavior omitted from release notes. Do not require a version bump for internal-only changes by default.
