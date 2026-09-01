# BedrockAntiDupe 2.7.0 Release Audit

## Target
- Paper 26.2
- Paper API 26.2.build.121-stable
- Java/JDK 25
- Java Edition

## Implemented in this source candidate
- Multi-pending player transactions
- Durable JSONL transaction journal
- SQLite transaction/economy persistence
- Data-component-aware ItemStack fingerprinting via Paper serialization
- Inventory click/drag/creative/craft/smithing tracking
- Player drop/pickup/consume tracking
- Hopper and hopper-minecart pickup/move telemetry
- Paper merchant purchase/trade context telemetry
- Shulker break integrity checks
- Incremental loaded-container scanner
- Durable recovery backup before destructive action
- Idempotent recovery lock and interrupted-restore preservation
- Async Discord network I/O
- Vault economy integration without making inventory detection depend on Vault
- JDK 25 CI verification

## Release gates
The following cannot be truthfully marked green from source inspection alone:

1. GitHub Actions `mvn clean verify` on JDK 25.
2. Runtime integration on an actual Paper 26.2 server.
3. Legitimate gameplay regression tests.
4. Crash/restart/player-data rollback simulation.
5. Real Vault/shop provider integration.
6. Real Discord webhook/rate-limit behavior.

A public release must not be labelled production-ready until those gates pass.
