# BedrockAntiDupe 2.4.0

Premium all-in-one inventory-integrity and anti-duplication protection for **Paper 26.2 / Java 25**.

## Target platform

- Paper 26.2
- Java 25
- Java Edition
- Geyser/Floodgate players are supported without making Floodgate mandatory

Paper 26.2 uses Java 25. This project intentionally does **not** target Java 21 for the 26.2 build.

## Core protection

- Main-thread inventory transaction capture
- Per-player transaction identity and same-tick burst protection
- Player inventory + viewed container conservation checks
- Inventory click and drag protection
- Hopper/container-to-player transaction monitoring
- Shulker box protection for all colors
- Shulker content-aware fingerprints
- Evidence logging
- Safe recovery backups before automatic removal
- Discord alerts with cooldowns
- Vault economy integration hooks with fail-safe rollback support
- Loaded-container integrity scan
- Impossible stack detection
- Staff notifications
- Operator/permission bypass for trusted testing
- Configurable tracked materials
- Configurable shop-title exclusions

## Safety model

The plugin does not treat every inventory increase as a dupe. Legitimate transfers must preserve the combined quantity of the player inventory and the observed container.

Automatic removal is **disabled by default**. Enable it only after testing the server's complete plugin stack.

When removal is enabled, removed stacks are backed up in the recovery vault before they are removed.

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

## Permissions

- `bedrockantidupe.admin`
- `bedrockantidupe.notify`
- `bedrockantidupe.debug`
- `bedrockantidupe.bypass`

## Build

GitHub Actions builds with **Temurin Java 25** and Maven.

Paper API is resolved from the official Paper Maven repository using the 26.2 build range.

## Important

No anti-dupe plugin can honestly guarantee detection of every future Mojang, Paper, plugin, or mod exploit. This project is designed as a defense-in-depth system: transaction conservation, container integrity, fingerprints, evidence, recovery, and configurable integrations work together rather than relying on one heuristic.

## License

Private server plugin. Copyright KingBrezzX.
