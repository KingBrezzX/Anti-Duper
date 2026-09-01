# Changelog

## 2.7.0
- Hardened transaction burst correlation.
- Added bundled SQLite persistence for transaction/economy history.
- Added durable append-only transaction journal with rotation.
- Added atomic/fsynced recovery vault.
- Fixed destructive-action ordering: recovery is written before inventory mutation.
- Revalidates affected inventory slots before destructive action.
- Added crafting/smithing transaction observation with safe transformation handling.
- Fixed automation false-positive risk from `InventoryMoveItemEvent` lifecycle.
- Added incremental loaded-container scanning with a per-tick budget.
- Added nested-shulker detection to player/container scans.
- Added economy correlation for confirmed shop-related duplication and safe Vault rollback.
- Added configuration validation and reload of persistence components.
- Added JDK 25 CI verification and Java 25 class-major check.
- Updated release documentation and third-party notices.
