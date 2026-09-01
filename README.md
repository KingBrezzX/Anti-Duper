# BedrockAntiDupe 2.7.4

Production-focused inventory integrity and anti-duplication protection for **Paper 26.2 / Java 25**.

> Despite the historical project name, the supported server platform is Java Edition. Bedrock/Geyser/Floodgate are not required.

## Scope

The plugin uses conservative transaction reconciliation rather than blindly cancelling inventory events. It records before/after player and viewed-container state, correlates rapid operations, fingerprints modern item state, and persists evidence.

Protection/monitoring covers inventory click/drag, creative inventory when enabled, player drop/pickup telemetry, crafting/smithing/trading context, shulker operations, hopper/container automation telemetry, nested shulkers, lifecycle boundaries, persistent journal, SQLite evidence, recovery, Vault economy correlation, and Discord alerts.

### Important safety model

A suspicious transaction is not automatically treated as a confirmed dupe. Destructive removal is disabled by default and requires a durable recovery backup plus a durable transaction journal. Recovery is all-or-nothing and never drops overflow into the world.

`InventoryMoveItemEvent` is intentionally treated as automation telemetry/reconciliation rather than an invented before-state because Paper documents that the source inventory may already have changed when that event fires.

## Commands

- `/antidupe status`
- `/antidupe reload`
- `/antidupe scan`
- `/antidupe scan loaded`
- `/antidupe cleanup`
- `/antidupe history`
- `/antidupe recovery`
- `/antidupe recovery restore <transaction-id>`
- `/antidupe debug`

## Permissions

- `bedrockantidupe.admin`
- `bedrockantidupe.notify`
- `bedrockantidupe.debug`
- `bedrockantidupe.bypass`

## Dependencies

- Paper 26.2
- Java 25
- Vault is optional and only required for economy rollback integration.
- SQLite JDBC is bundled in the JAR.

## Verification

GitHub Actions performs:
1. JDK 25 setup
2. `mvn clean verify`
3. automated unit/release-contract tests
4. JAR/plugin resource verification
5. Java class-major verification (69 / Java 25)
6. release artifact upload

A green CI build proves compilation and automated tests. It does **not** prove every real-world Minecraft exploit on a live server. Live staging tests are still required before making a public security guarantee.

## Configuration

See `src/main/resources/config.yml` and `src/main/resources/messages.yml`.

Do not enable destructive removal on a live server until the plugin has been staged with your exact Paper build and plugin stack.


## Third-party shop integration

The plugin does not claim universal compatibility based on GUI titles. Shop plugins that know the exact transaction result should call `ShopTransactionListener.recordExternalShopTransaction(...)`, then record the exact economy value through `recordEconomyTransaction(...)`. Paper 26.2 merchant purchases are handled natively.

## Release verification

CI verifies Java 25, Paper 26.2 startup, automated regression tests, JAR metadata, SQLite initialization, and command startup. Real-player exploit regression still requires a staging server/client because a headless Paper process cannot honestly simulate every network inventory interaction.

### Release gate
The project deliberately distinguishes deterministic automated regression from real-player exploit testing. A green CI build proves compilation, unit/regression tests, Paper startup, and command startup; it does not claim that every client/network exploit has been reproduced.
