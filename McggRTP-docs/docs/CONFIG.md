# McggRTP Config Plan

This file describes the original suggested configs for the Paper and Velocity plugins. Some names changed during implementation; the current authoritative config examples are in the root `README.md` and `DEPLOYMENT.md`.

## Paper config.yml

Paper controls GUI layout and world RTP rules.

```yaml
gui:
  title: "&8Random Teleport"
  size: 27
  filler:
    enabled: true
    material: BLACK_STAINED_GLASS_PANE
    name: " "

main-menu:
  overworld:
    slot: 11
    display-name: "&aOverworld"
    material: GRASS_BLOCK
    world-name: world
    permission: "mcggrtp.dimension.overworld"
    lore:
      - "&7Click to choose an overworld server."

  nether:
    slot: 13
    display-name: "&cNether"
    material: NETHERRACK
    world-name: world_nether
    permission: "mcggrtp.dimension.nether"
    lore:
      - "&7Click to choose a nether server."
      - "&cDangerous dimension."

  end:
    slot: 15
    display-name: "&dThe End"
    material: END_STONE
    world-name: world_the_end
    permission: "mcggrtp.dimension.end"
    lore:
      - "&7Click to choose an end server."

server-menu:
  title: "&8Choose Server: {dimension}"
  size: 27
  online-material: LIME_WOOL
  offline-material: BARRIER

rtp:
  worlds:
    world:
      enabled: true
      center-x: 0
      center-z: 0
      min-radius: 500
      max-radius: 5000
      max-attempts: 32
      blacklisted-biomes:
        - OCEAN
        - DEEP_OCEAN
      unsafe-blocks:
        - LAVA
        - FIRE
        - CACTUS
        - MAGMA_BLOCK

    world_nether:
      enabled: true
      center-x: 0
      center-z: 0
      min-radius: 250
      max-radius: 3000
      max-attempts: 64
      avoid-bedrock-roof: true
      unsafe-blocks:
        - LAVA
        - FIRE
        - MAGMA_BLOCK

    world_the_end:
      enabled: true
      center-x: 0
      center-z: 0
      min-radius: 500
      max-radius: 5000
      max-attempts: 48
```

## Velocity config.yml

Velocity controls network server groups, cooldowns, and pending RTP.

```yaml
settings:
  plugin-message-channel: "mcggrtp:main"
  pending-expire-seconds: 30

cooldowns:
  enabled: true
  default-seconds: 300
  bypass-permission: "rtp.bypass.cooldown"

servers:
  survival-1:
    display-name: "&aSurvival 1"
    enabled: true
    permission: "rtp.server.survival1"

  survival-2:
    display-name: "&aSurvival 2"
    enabled: true
    permission: "rtp.server.survival2"

  survival-3:
    display-name: "&aSurvival 3"
    enabled: true
    permission: "rtp.server.survival3"

dimensions:
  overworld:
    servers:
      - survival-1
      - survival-2
      - survival-3

  nether:
    servers:
      - survival-1
      - survival-2

  end:
    servers:
      - survival-1
```

## Messages config

You can keep messages in a shared `messages.yml` on Paper.

```yaml
prefix: "&8[&aMcggRTP&8] "
no-permission: "&cYou do not have permission."
cooldown: "&cYou must wait &e{time}&c before using RTP again."
searching: "&7Searching for a safe location..."
teleport-success: "&aTeleported you to a random location."
teleport-failed: "&cCould not find a safe location. Try again later."
sending-server: "&7Sending you to &e{server}&7..."
server-offline: "&cThat server is offline."
```
