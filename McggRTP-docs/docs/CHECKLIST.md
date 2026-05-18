# McggRTP Implementation Checklist

## Project setup

- [ ] Create multi-module Gradle project.
- [ ] Add `common`, `velocity`, and `paper` modules.
- [ ] Set Java toolchain to 21.
- [ ] Set group to `me.mcgg.azreyzaako`.
- [ ] Set plugin author to `azreyzaako`.
- [ ] Configure jar names:
  - [ ] `McggRTP-velocity.jar`
  - [ ] `McggRTP-paper.jar`

## Paper plugin

- [ ] Create main class `me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper`.
- [ ] Create `paper-plugin.yml`.
- [ ] Register `/rtp` command.
- [ ] Create main dimension GUI.
- [ ] Create server submenu GUI.
- [ ] Add inventory click listener.
- [ ] Add config loader.
- [ ] Add safe location finder.
- [ ] Add teleport service.
- [ ] Add join listener to check pending RTP.

## Velocity plugin

- [ ] Create main class `me.mcgg.azreyzaako.mcggrtp.velocity.McggRTPVelocity`.
- [ ] Create `velocity-plugin.json`.
- [ ] Register plugin message channel `mcggrtp:main`.
- [ ] Create pending RTP manager.
- [ ] Create server transfer service.
- [ ] Add cooldown manager.
- [ ] Reply to `CHECK_PENDING_RTP`.
- [ ] Clear expired pending RTP requests.

## Common module

- [ ] Add `MessageType` enum.
- [ ] Add `PendingRtp` record.
- [ ] Add message codec helper.
- [ ] Add shared channel constant.

## RTP safety

- [ ] Check target world exists.
- [ ] Check world is enabled.
- [ ] Check world border.
- [ ] Check block below is safe.
- [ ] Check feet block is passable.
- [ ] Check head block is passable.
- [ ] Check unsafe block blacklist.
- [ ] Check biome blacklist.
- [ ] Add max attempt limit.

## GUI polish

- [ ] Add configurable item names.
- [ ] Add configurable lore.
- [ ] Add locked item for missing permission.
- [ ] Add offline item for offline server.
- [ ] Show player count in server lore.
- [ ] Add sounds.

## Final testing

- [ ] Test `/rtp` from server-1 to server-1.
- [ ] Test `/rtp` from server-1 to server-2.
- [ ] Test Overworld RTP.
- [ ] Test Nether RTP.
- [ ] Test End RTP.
- [ ] Test player disconnect during pending RTP.
- [ ] Test target server offline.
- [ ] Test cooldown.
- [ ] Test permissions.
```
