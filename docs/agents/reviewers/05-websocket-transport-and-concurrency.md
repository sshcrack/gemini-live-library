# Prompt: WebSocket transport and concurrency reviewer

Read `docs/agents/reviewers/COMMON.md` and review the diff from `<FIXED_POINT>` below the Gemini-domain layer: WebSocket framing/client/server code, TLS channels, socket I/O, timers, threads, queues, and lifecycle ownership.

## Review for

- Races between connect/open/message/close/reconnect paths or callback threads.
- Non-atomic lifecycle state transitions that allow sends on closing/closed sockets, duplicate close callbacks, or reconnecting an invalid instance.
- Timer/task/thread resources that survive close, are cancelled out of order, or race with replacement tasks.
- Blocking operations on event-loop/read/write threads that can stall heartbeats, message delivery, or shutdown.
- Buffer misuse: assuming `ByteBuffer.array()` is available or covers only the meaningful slice, losing position/limit, partial writes/reads, or reusing mutable buffers across threads.
- TLS handshake/delegated-task state bugs, selector interest-op mistakes, busy loops, or missing wakeups.
- Frame fragmentation/control-frame/close-handshake violations exposed by the change.
- Executor/thread ownership leaks and shutdown paths that can hang JVM termination.
- Exception paths that leave connection state inconsistent or suppress the original transport failure.
- Changes in vendored/forked WebSocket code that diverge from surrounding invariants without a regression test.

Trace thread ownership explicitly for every concurrency finding. A hypothetical race without two reachable interleavings is not a finding.
