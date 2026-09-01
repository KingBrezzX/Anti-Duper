# BedrockAntiDupe 2.6.0 Release Audit

## Target
- Paper 26.2
- Paper API 26.2.build.121-stable
- Java/JDK 25
- Java Edition

## Safety changes
- Transaction bursts are coalesced instead of overwriting or creating one task per click.
- Inventory automation events are not treated as naive before/after transactions because Paper may already have removed the source item when `InventoryMoveItemEvent` fires.
- Destructive removal is opt-in and requires durable recovery.
- Recovery is written atomically and fsynced before removal.
- The affected inventory slots are revalidated immediately before removal.
- A durable transaction journal entry is written before destructive removal.
- Economy rollback is independent of ordinary inventory confirmation.
- Loaded-container scanning is incremental and never loads chunks.
- SQLite persistence is bundled and writes are serialized asynchronously.

## Paper 26.2 API usage
- Data Component-aware item fingerprinting through serialized item state.
- Paper native shulker duplication telemetry.
- Hopper search telemetry.
- Inventory/Craft/Smith/Creative transaction observation.

## Known limitations
This plugin cannot guarantee prevention of every future exploit. New Mojang/Paper/plugin-specific exploits require new rules or adapters. The plugin therefore favors conservation checks, persistent evidence, recovery, and safe confirmation rather than destructive heuristics.

## Required release verification
GitHub Actions must pass on JDK 25. A staging server running Paper 26.2 must be used to test normal inventory transfers, crafting, smithing, shulkers, hopper automation, reconnect/logout, economy integration, recovery, Discord failure, and restart/crash recovery before publishing a production release.
