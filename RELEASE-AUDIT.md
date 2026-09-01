# BedrockAntiDupe 2.5.0 — Release Audit

Target: Paper 26.2, Java 25, Java Edition.

## Fixed in this source pass

- Fixed the per-player transaction overwrite design by allowing multiple pending transaction IDs.
- Added persistent append-only transaction journal.
- Registered the Paper-native prevention/telemetry listener and player-quit transaction flush listener.
- Corrected ShulkerDuplicateEvent handling: it is an entity duplication mechanic, not an item-duplication event, so it is monitored rather than globally cancelled.
- Added Paper 26.2 Data Component-aware item fingerprints using `ItemStack#getDataTypes()` plus the canonical NBT byte serialization.
- Recovery now uses `serializeAsBytes()`/`deserializeBytes()` and destructive removal is refused unless the recovery backup is durably written first.
- Removed unconditional shop-title bypass from the inventory detector; shop clicks are correlated with a Vault balance change when Vault is available.
- Creative inventory monitoring now follows the `protection.creative-events` configuration instead of being unconditionally skipped.
- Added repeated-signal confirmation to reduce false positives from a single legitimate inventory mutation.
- GitHub Actions builds with JDK 25 and verifies Java class major version 69.
- Paper API is pinned to `26.2.build.121-stable` for reproducible release builds.

## Not honestly claimable from this environment

A runtime server test on Paper 26.2 + JDK 25 was not possible in this environment because only JDK 21 and no Maven/Paper dependency cache are available. Therefore this source is **not labelled production-certified** until the GitHub Actions build is green and a real Paper 26.2/JDK 25 staging server passes the integration matrix.

## Required staging tests

1. Normal chest/barrel/shulker transfers.
2. Shift-click, drag, double-click, number-key and offhand inventory operations.
3. Hopper/container automation in both directions.
4. Drop/pickup and item entity merges.
5. Crafting, smelting, anvil, smithing and villager trading.
6. Shop purchase/sale with Vault and without Vault.
7. Shulker entity duplication behavior must remain vanilla and must not be disabled.
8. Server restart, player quit/rejoin and forced-save scenarios.
9. High-load/TPS degradation tests.
10. Recovery restore after an intentionally confirmed test transaction.
11. Discord webhook success/failure and rate limiting.
12. Loaded-container scan on a large loaded area.
13. Memory/CPU observation over at least 2 hours.

No future exploit can be guaranteed absent. The plugin is designed for defense-in-depth and must remain updated with Paper/Minecraft security fixes.
