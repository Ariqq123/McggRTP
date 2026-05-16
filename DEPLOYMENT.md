# McggRTP Deployment Guide

This project deploys as two plugins:

- Velocity: `McggRTP-velocity.jar`
- Paper: `McggRTP-paper.jar`

Use the shaded jars, not the `-thin` jars.

## Requirements

- Java `21`
- One Velocity proxy
- One or more Paper `1.21.x` backend servers
- Velocity modern forwarding enabled
- Paper backends configured for Velocity forwarding

## Build

From the repo root:

```bash
GRADLE_USER_HOME=/tmp/mcggrtp-gradle-home ./gradlew build
```

Artifacts:

- `paper/build/libs/McggRTP-paper.jar`
- `velocity/build/libs/McggRTP-velocity.jar`

## Install on Velocity

Copy:

- `velocity/build/libs/McggRTP-velocity.jar`

To:

- `plugins/` in your Velocity server

Start Velocity once. It will generate:

- `plugins/mcggrtp/config.yml`

Edit that file to match your real network.

Important sections:

- `settings.plugin-message-channel`
- `cooldowns`
- `servers`
- `dimensions`

Example:

```yml
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

dimensions:
  overworld:
    servers: ["survival-1", "survival-2"]
  nether:
    servers: ["survival-1"]
  end:
    servers: ["survival-1"]
```

## Configure Velocity forwarding

In `velocity.toml`:

```toml
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
```

Your `[servers]` block must use the same server ids referenced in McggRTP config:

```toml
[servers]
survival-1 = "127.0.0.1:25566"
survival-2 = "127.0.0.1:25567"
try = ["survival-1"]
```

## Install on each Paper backend

Copy:

- `paper/build/libs/McggRTP-paper.jar`

To:

- `plugins/` on every backend server that should support RTP

Start each Paper server once. It will generate:

- `plugins/McggRTP-Paper/config.yml`
- `plugins/McggRTP-Paper/messages.yml`

## Configure each Paper backend

In each backend `plugins/McggRTP-Paper/config.yml`, set:

- `network.current-server`
- dimension world names
- GUI/server mappings
- RTP settings per world

Example for `survival-1`:

```yml
network:
  current-server: "survival-1"
  dimensions:
    overworld:
      servers: ["survival-1", "survival-2"]
    nether:
      servers: ["survival-1"]
    end:
      servers: ["survival-1"]
  servers:
    survival-1:
      display-name: "&aSurvival 1"
      permission: "rtp.server.survival1"
    survival-2:
      display-name: "&aSurvival 2"
      permission: "rtp.server.survival2"
```

On `survival-2`, change only:

```yml
network:
  current-server: "survival-2"
```

World-name entries must match real Bukkit world names on that backend:

- `world`
- `world_nether`
- `world_the_end`

## Configure Paper for Velocity

In each backend `config/paper-global.yml`:

```yml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "your-forwarding-secret"
```

That secret must match Velocity's `forwarding.secret`.

In backend `server.properties`:

- `online-mode=false`

## Permissions

The plugin uses these permissions:

- `rtp.use`
- `rtp.dimension.overworld`
- `rtp.dimension.nether`
- `rtp.dimension.end`
- `rtp.server.survival1`
- `rtp.server.survival2`
- `rtp.server.survival3`
- `rtp.bypass.cooldown`
- `rtp.admin.reload`

Defaults:

- normal RTP permissions are enabled by default
- `rtp.bypass.cooldown` is op-only
- `rtp.admin.reload` is op-only

If you use LuckPerms, assign them there instead of relying on defaults.

## Start order

Recommended:

1. Start Velocity
2. Start all Paper backends
3. Join through Velocity
4. Test `/rtp`

## Basic validation

Test these:

- `/rtp` on `survival-1` to `survival-1`
- `/rtp` on `survival-1` to `survival-2`
- Overworld RTP
- Nether RTP
- End RTP
- cooldown denial after a successful RTP
- offline server display in the server menu
- `/rtp reload` as an admin

## Troubleshooting

If `/rtp` does nothing:

- confirm `McggRTP-paper.jar` is the shaded jar
- check Paper console for plugin enable errors
- confirm Velocity forwarding is configured correctly

If cross-server RTP fails:

- confirm Velocity server ids match `network.current-server`
- confirm Velocity `[servers]` names match McggRTP config names exactly
- confirm the target backend is reachable by Velocity

If servers appear online when they should not:

- check the Velocity-side McggRTP config `servers.*.enabled`
- check backend reachability from the proxy

If plugin message flow fails:

- confirm both sides use `mcggrtp:main`
- confirm both deployable jars are the shaded ones

If reload does not sync:

- `/rtp reload` reloads Paper locally and asks Velocity to reload proxy config
- check both proxy and backend logs for reload errors

## Files to Use

Deploy:

- `velocity/build/libs/McggRTP-velocity.jar`
- `paper/build/libs/McggRTP-paper.jar`

Reference:

- `IMPLEMENTATION_STATUS.md`
- `.integration/runtime/validation-report.json`
