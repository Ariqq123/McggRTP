# McggRTP Implementation Status

This file compares the current implementation against `McggRTP-docs`.

## Covered by code and build verification

- Multi-module Gradle project exists with `common`, `paper`, and `velocity`.
- Java 21 toolchain is configured.
- Shared message codec and channel constant exist.
- Paper plugin registers `/rtp`, opens a dimension menu, and opens a proxy-backed server submenu.
- Velocity stores pending RTP requests and replies to pending checks on join.
- Paper performs safe random teleport with max attempts, world border checks, block passability checks, unsafe block blacklist checks, biome blacklist checks, and Nether bedrock-roof avoidance.
- Cooldown enforcement is proxy-backed for both same-server and cross-server RTP requests.
- GUI renders offline and locked server states and shows player counts in lore.
- `/rtp reload` reloads Paper state locally and asks Velocity to reload proxy config.
- Build verification passes with `GRADLE_USER_HOME=/tmp/mcggrtp-gradle-home ./gradlew build`.
- Deployable plugin jars are shaded so the shared `common` classes are present at runtime in both `McggRTP-paper.jar` and `McggRTP-velocity.jar`.
- Unit tests exist for the shared message codec and the Velocity cooldown/pending managers.
- Velocity-side tests also cover message-listener handling for cooldown checks, server status responses, pending RTP lookup, and reload acknowledgements.
- Paper-side tests cover command permission behavior, GUI click handling for same-server and cross-server paths, offline and permission-denied server clicks, proxy message-bridge response handling, join-time pending checks, safe-location search behavior, and reaching the GUI-open path under MockBukkit.

## Covered by live validation

Live network validation was run with:

- `python3 integration/run_live_validation.py`

The latest report is written to:

- `.integration/runtime/validation-report.json`

- `/rtp` from `survival-1` to `survival-1`
- `/rtp` from `survival-1` to `survival-2`
- Overworld RTP in a live world
- Nether RTP in a live world
- End RTP in a live world
- Target server offline behavior in a real network
- Cooldown in a live network

The live report includes:

- same-server overworld RTP result and final position
- live cooldown denial after a successful local RTP
- live Nether RTP with the `We Need to Go Deeper` advancement
- live End RTP with `The End?` advancement
- live offline-target denial message
- proxy log evidence for a real cross-server transfer to `survival-2`
- backend-2 log evidence that the player reached the second Paper server

## Covered by targeted automated tests

These checklist items are covered directly by focused tests rather than the live bot harness:

- Player disconnect during pending RTP:
  `VelocityMessageListenerTest.pendingRequestExpiresForDisconnectedFlow`
- Permissions:
  `RtpCommandTest.reloadRequiresAdminPermission`
  `RtpGuiListenerTest.mainMenuDeniedWithoutDimensionPermission`
  `RtpGuiListenerTest.serverMenuDeniesWithoutServerPermission`

## Notes

- Paper now has a `network.current-server` setting because a backend server cannot reliably infer its Velocity alias at runtime.
- MockBukkit does not implement Bukkit inventory creation deeply enough for full GUI interaction tests here, so the menu rendering tests are currently limited by the test framework rather than the plugin code.
- The current verification evidence is build success, unit tests, targeted listener tests, and a disposable live Velocity + two-Paper validation harness under `integration/`.
