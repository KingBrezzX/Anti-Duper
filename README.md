# BedrockAntiDupe

**Powerful all-in-one anti-duplication and inventory-integrity protection by KingBrezzX.**

## Compatibility
- Paper 26.2
- Java/JDK 25
- Java Edition
- Vault optional
- Floodgate/Geyser optional

## Protection layers
- Inventory transaction conservation
- Burst transaction correlation
- Shulker/container fingerprinting
- Modern serialized item/Data Component-aware fingerprints
- Crafting and smithing transaction observation
- Hopper automation telemetry
- Impossible-stack detection
- Nested-shulker detection
- Incremental loaded-container scanning
- Persistent JSONL transaction journal
- Bundled SQLite transaction/economy history
- Durable recovery vault
- Evidence files
- Optional Discord webhook
- Optional Vault economy rollback
- Staff notifications
- Safe opt-in automatic removal

## Commands
- `/antidupe`
- `/antidupe reload`
- `/antidupe status`
- `/antidupe scan`
- `/antidupe scan loaded`
- `/antidupe cleanup`
- `/antidupe history`
- `/antidupe recovery`
- `/antidupe recovery restore <transaction-id>`
- `/antidupe debug`

## Safety
Automatic item removal is disabled by default. When enabled, recovery must succeed and the affected inventory slots must still exactly match the detected post-state before anything is removed.

## Build
Use JDK 25 and Maven. GitHub Actions builds and verifies the JAR automatically.

See `RELEASE-AUDIT.md` for the final verification checklist and limitations.
