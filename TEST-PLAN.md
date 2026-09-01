# Test Plan — BedrockAntiDupe 2.7.3

## CI automated
- Java 25 compilation.
- JUnit validation tests.
- Release resource/JAR contract checks.
- Paper 26.2 startup smoke test using Paper 26.2 build 121.

## Staging gameplay matrix
Run on a copy of the production world:
- inventory click/drag/shift/number-key/double-click
- drop and player pickup
- chest/barrel/shulker/container minecart
- hopper/dropper/dispenser chains
- crafting/smithing/anvil
- villager trading
- death/respawn/teleport
- logout/reconnect
- restart and crash simulation
- recovery restore with full and partial inventory capacity
- Vault economy transaction and provider failure
- shop plugin integration
- Discord webhook failure/rate limit
- concurrent player activity

## Acceptance
No item loss, no duplicate restoration, no false-positive action on legitimate gameplay, no synchronous disk/network work in event handlers, no unbounded cache growth, and no plugin startup errors.

CI green is necessary but not sufficient for a blanket “dupe-proof” claim.
