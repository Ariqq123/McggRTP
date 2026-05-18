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

- `debug.enabled`
- `settings.plugin-message-channel`
- `cooldowns`
- `servers`
- `dimensions`

Example:

```yml
debug:
  enabled: false

settings:
  plugin-message-channel: "mcggrtp:main"
  pending-expire-seconds: 30

cooldowns:
  enabled: true
  default-seconds: 300
  bypass-permission: "mcggrtp.bypass.cooldown"
  server-permission-prefix: "mcggrtp.server."

servers:
  survival-1:
    display-name: "&aSurvival 1"
    enabled: true
  survival-2:
    display-name: "&aSurvival 2"
    enabled: true

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

- `debug.enabled`
- `network.current-server`
- dimension world names
- GUI/server mappings
- per-dimension `warmup-seconds`
- RTP settings per world
- adaptive RTP throttle settings

Example for `survival-1`:

```yml
debug:
  enabled: false

network:
  current-server: "survival-1"
  server-permission-prefix: "mcggrtp.server."
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
    survival-2:
      display-name: "&aSurvival 2"
```

On `survival-2`, change only:

```yml
network:
  current-server: "survival-2"
```

For production load, tune Paper-side concurrency under `rtp`:

```yml
rtp:
  max-concurrent-searches: 8
  adaptive-throttle:
    enabled: true
    min-concurrent-searches: 1
    min-tps: 18.5
    max-mspt: 80.0
    queue-start-delay-ticks: 2
    metrics-log-interval: 25
```

The adaptive throttle lowers active RTP searches when TPS/MSPT crosses the configured thresholds and recovers gradually when the backend is healthy. Enable `debug.enabled` to log queue wait, search duration, attempts, generated-first ratio, and the current adaptive limit.

Warmup is enforced on the Paper backend before either:
- a same-server RTP cooldown check and local teleport
- a cross-server pending request to Velocity

If you are troubleshooting RTP or proxy messaging, set `debug.enabled: true` on
both the Velocity and Paper configs. McggRTP will emit verbose logs for
pending-RTP checks, cooldown checks, server-status requests, transfers, and RTP
search/teleport completion.

Server permissions are dynamic by default. If you omit `permission` for a server,
McggRTP derives `<server-permission-prefix><server-id>`.
Derived server permissions are registered as default-allowed on Paper, so players do not need op just to use configured RTP servers.
Examples:
- with `mcggrtp.server.`: `survival-2` -> `mcggrtp.server.survival-2`
- with `mcgg.server.`: `skyblock321` -> `mcgg.server.skyblock321`

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

- `mcggrtp.use`
- `mcggrtp.dimension.overworld`
- `mcggrtp.dimension.nether`
- `mcggrtp.dimension.end`
- `mcggrtp.server.<server-id>`
- `mcggrtp.bypass.cooldown`
- `mcggrtp.admin.reload`

Defaults:

- normal RTP permissions are enabled by default
- dynamic `mcggrtp.server.<server-id>` permissions are enabled by default for normal players
- `mcggrtp.bypass.cooldown` is op-only
- `mcggrtp.admin.reload` is op-only

Use LuckPerms to deny or grant specific server RTP permissions when you want per-server access control.

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
- Paper also merges any newly added default keys into `config.yml` and `messages.yml` during startup and `/rtp reload`, while keeping your existing values
- check both proxy and backend logs for reload errors

## Files to Use

Deploy:

- `velocity/build/libs/McggRTP-velocity.jar`
- `paper/build/libs/McggRTP-paper.jar`

Reference:

- `IMPLEMENTATION_STATUS.md`
- `.integration/runtime/validation-report.json`
