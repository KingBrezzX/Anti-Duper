# Changelog


## 2.7.4 — Final CI/runtime hardening
- Removed the hard Vault class linkage from the core `ExploitProtectionListener`.
- Kept Vault economy support optional; core anti-dupe startup no longer depends on Vault.
- Moved shop balance reads behind `EconomyRollbackManager`'s optional integration boundary.
- Added a live Paper 26.2 ItemStack/Data Components self-test during plugin startup.
- Made CI fail if core listener registration or the Paper runtime self-test fails.
- Kept Java 25 / Paper 26.2 verification and deterministic JUnit coverage.


## 2.7.3
- Final hardening release candidate for Paper 26.2 / Java 25.
- Fixed shop/economy transaction correlation to reuse the active ledger transaction ID.
- Added lifecycle transaction fences for death, respawn, teleport, quit, and graceful shutdown.
- Made recovery restore all-or-nothing with capacity preflight; recovery never spills overflow into the world.
- Added journal write locking so synchronous durable entries cannot race asynchronous writes/rotation.
- Hardened rollback result validation against NaN/infinity/over-reporting.
- Removed Bedrock-only soft dependencies; Java Edition is the supported platform.
- Added JUnit release-contract and economy/rollback tests.
- CI now requires tests to actually execute before accepting the release artifact.
- Updated release verification to version 2.7.3.

## 2.7.3
- Java Edition only; removed Geyser/Floodgate runtime dependency assumptions.
- Added public third-party shop transaction integration point.
- Added stronger transaction-conservation regression tests.
- Added slot-aware, idempotent destructive recovery records with interrupted-recovery reconciliation.
- CI now verifies Paper 26.2 startup and command path in addition to compilation/tests.

## 2.7.4

- Fixed CI Paper 26.2 runtime smoke download to use the official Downloads Service v3 with a valid User-Agent.
- Fixed release workflow references from 2.7.3 to 2.7.4.
- Added release-version consistency checks before runtime smoke testing.
- Runtime smoke failures are no longer masked by `|| true`.
- Added explicit runtime-staging gate workflow.
- Added process-kill/restart recovery smoke script.
- Added real-player regression test plan.
- Added third-party shop compatibility contract.
- Release status remains evidence-gated; no unverified runtime claim is promoted to READY.
