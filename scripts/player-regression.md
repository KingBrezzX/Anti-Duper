# Real-player regression gate

This gate must be run with a real Java Edition client (or a protocol-accurate client harness) against the disposable Paper 26.2 staging server.

Required cases:
- normal click / drag / shift-click / number-key / double-click / middle-click
- drop / pickup
- shulker open/close and move
- hopper and hopper-minecart transfer
- dropper/dispenser/container minecart automation
- crafting / smithing / anvil / villager trade
- disconnect during inventory transaction
- reconnect and repeat
- teleport/death/respawn boundaries

For every case record: before total, after total, transaction id, journal state, and whether any unexpected positive item delta occurred.

**A CI compile cannot mark this gate PASS.** It requires a live player/client session.
