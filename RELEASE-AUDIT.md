# Release Audit — 2.7.4

## Verified in source
- Paper API target: 26.2 build 121 stable.
- Maven compiler release: Java 25.
- Java Edition only; Vault optional.
- Transaction journal and SQLite persistence.
- Durable recovery backup.
- Recovery capacity preflight and idempotent claim state.
- Lifecycle transaction fences.
- Modern item serialization/fingerprinting.
- Bounded in-memory caches.
- Async database/journal workers do not call Bukkit API.
- Destructive removal requires durable journal + recovery.
- GitHub Actions verifies class major 69.

## Automated tests
JUnit tests cover:
- EconomyTransaction validation and identity.
- RollbackResult finite/clamped behavior.
- Required release resources.

## Not provable from static/CI testing
- Every historical Minecraft duplication exploit.
- Correct behavior with every third-party shop plugin.
- Live Discord webhook delivery/rate limits.
- Live Vault provider behavior.
- Crash recovery after arbitrary OS/server power loss.
- TPS impact under a real production workload.

## Release gate
Status: RELEASE CANDIDATE. Automated gates may be green, but real-client exploit and physical power-loss tests require a staging environment and are never marked green from static analysis alone.

A public release should additionally be staged on the exact Paper 26.2 build used by the server with a backup and representative plugin stack. Never advertise “100% dupe-proof”; anti-dupe software can reduce known exploit classes but cannot guarantee against unknown future server/client/plugin bugs.

## 2.7.4 hardening
- Destructive recovery is slot-aware and refuses ambiguous/mixed inventory state.
- Recovery uses versioned `.recovery`, `.restoring`, and `.restored` states.
- Third-party shops can provide exact transaction identity/result instead of relying on GUI titles.
- Java Edition is the only supported runtime platform.
- CI covers deterministic conservation regressions and Paper startup/command smoke checks.

Real exploit/client regression still requires a real player-connected staging server; no headless CI test is represented as equivalent to that.


## 2.7.4 transaction-correlation hardening
- Transactions within the burst window are reused only when source and viewed-container identity match.
- Shutdown flush preserves the currently open top inventory when it matches the transaction snapshot.
- Reconciliations are scheduled per transaction instead of one task per player, preventing later transactions from being stranded.
- Merchant purchases begin a pre-event transaction fence and Paper 26.2 purchase context is correlated afterward.
- GUI shop context never invents a random transaction ID when no transaction exists.
- Paper 26.2 PlayerPickItemEvent is included in transaction capture.

## 2.7.4 final-gate policy

A production release is not marked READY from compilation alone. Runtime gates are evidence-based:
- Real-player regression requires a live Java Edition client.
- Power-loss gate requires a disposable staging server and process-kill/restart test; host power-cut remains a separate stronger test.
- Third-party shop compatibility requires an exact plugin name/version and a live integration run.

No gate may be marked PASS without its evidence.
