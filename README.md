# McggRTP

McggRTP is a random teleport plugin built for a Velocity network with Paper backend servers. It provides a `/rtp` GUI, same-server RTP, cross-server RTP, proxy-owned cooldowns, server status in the GUI, and production-oriented queueing for high-concurrency RTP requests.

## Goal

McggRTP aims to provide a reliable, permission-friendly, and performance-aware random teleport system for Velocity + Paper networks.

The plugin is designed around a few production goals:

- RTP success must mean the player was actually teleported to a safe destination, not merely transferred between servers.
- Cross-server RTP must be verifiable from the target Paper backend.
- Normal players should be able to use RTP without being op, while LuckPerms can still restrict specific servers or dimensions.
- High-concurrency RTP should be controlled with queueing, adaptive throttling, and idle safe-location pooling instead of unbounded chunk searches.
- Stress validation should report both RTP results and server health signals such as TPS/MSPT.
- Test harness limitations, especially bot protocol artifacts, should be separated from real plugin behavior.

The project builds two deployable plugins:

- `McggRTP-paper.jar` for every Paper backend that should support RTP
- `McggRTP-velocity.jar` for the Velocity proxy

Use the shaded jars from `paper/build/libs/` and `velocity/build/libs/`. Do not deploy the `-thin` jars.

## Repository Guide

Use this README as the current entry point for building, installing, and understanding the plugin.

Helpful files:

- [DEPLOYMENT.md](DEPLOYMENT.md): step-by-step installation and production configuration guide.
- [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md): current implementation and validation coverage.
- [McggRTP-docs/](McggRTP-docs): original design notes and planning references. These are useful for learning the project history, but the root README and current source code are authoritative when behavior differs.
- [integration/](integration): disposable Velocity + Paper validation harness and Mineflayer stress scripts.

Module layout:

```text
McggRTP/
  common/      shared plugin-message records, constants, and codec
  paper/       Paper command, GUI, warmup, safe-location search, teleport logic
  velocity/    Velocity cooldowns, server status, pending RTP, server transfer
  integration/ live-network and stress validation harnesses
```

Suggested reading order for new contributors:

1. Read the Goal and Architecture sections in this README.
2. Build the project once with `./gradlew build`.
3. Read [DEPLOYMENT.md](DEPLOYMENT.md) before installing on a real network.
4. Check [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) to understand what has already been verified.

## Features

- `/rtp` opens a GUI instead of teleporting immediately.
- Dimension menu for Overworld, Nether, and End RTP targets.
- Server submenu backed by live Velocity server status.
- Same-server RTP on the current Paper backend.
- Cross-server RTP through Velocity transfer and pending RTP state.
- Proxy-owned cooldowns for both local and cross-server RTP.
- Per-dimension warmup before RTP starts.
- Warmup cancellation when the player moves.
- Safe-location checks for world border, passable feet/head, unsafe blocks, biome blacklist, bedrock floor, and Nether roof avoidance.
- Offline server detection in the GUI.
- Locked GUI state for missing permissions.
- Player counts in server lore.
- `/rtp reload` for Paper config reload and Velocity config reload request.
- Debug mode for tracing GUI, plugin messaging, cooldowns, pending RTP, transfers, and teleport completion.
- Paper-side RTP queueing with `rtp.max-concurrent-searches` for production servers with many simultaneous RTP requests.
- Adaptive Paper-side RTP throttling based on backend TPS/MSPT.
- Idle safe-location pooling to reduce player-facing RTP search time without generating new chunks by default.
- Automatic Paper `config.yml` and `messages.yml` default-key merge on startup and `/rtp reload`.
- Strict integration stress checks that require cross-server RTP confirmation from the destination server.

## Architecture

McggRTP is split into three Gradle modules:

- `common`: shared plugin message channel, message records, and codec logic.
- `paper`: `/rtp`, GUI, warmup, safe-location search, and actual teleport execution.
- `velocity`: pending RTP state, cooldown state, server status, and server transfer.

Runtime flow:

1. A player runs `/rtp` on a Paper backend.
2. Paper opens the dimension menu.
3. The player chooses a dimension and target server from the GUI.
4. Paper runs warmup.
5. For same-server RTP, Paper asks Velocity for cooldown state, then teleports locally.
6. For cross-server RTP, Paper asks Velocity to create pending RTP and transfer the player.
7. The target Paper backend checks for pending RTP after join and completes the teleport.
8. Paper sends completion back to Velocity so pending RTP state can be cleared.

The plugin message channel is `mcggrtp:main`. You do not create this channel manually; both plugins register it in code.

## Requirements

- Java 21
- Velocity 3.x
- Paper 1.21.x backends
- Velocity modern forwarding enabled
- Paper backends configured for Velocity forwarding
- The Velocity and Paper McggRTP plugins installed together

## Build

From the repository root:

```bash
./gradlew build
```

If you want to isolate Gradle cache usage:

```bash
GRADLE_USER_HOME=/tmp/mcggrtp-gradle-home ./gradlew build
```

Build outputs:

- `paper/build/libs/McggRTP-paper.jar`
- `velocity/build/libs/McggRTP-velocity.jar`

The `paper/build/libs/McggRTP-paper-thin.jar` and `velocity/build/libs/McggRTP-velocity-thin.jar` files are intermediate thin jars. Use the shaded jars listed above for server deployment.

## Deployment

1. Build the project.
2. Put `velocity/build/libs/McggRTP-velocity.jar` in the Velocity `plugins/` directory.
3. Put `paper/build/libs/McggRTP-paper.jar` in the `plugins/` directory of each Paper backend.
4. Start Velocity and all Paper servers once so configs are generated.
5. Configure Velocity server ids and Paper `network.current-server` values so they match exactly.
6. Join through Velocity and test `/rtp`.

Recommended startup order:

1. Start Velocity.
2. Start every Paper backend.
3. Join through Velocity.
4. Run `/rtp`.

Full deployment details are in [DEPLOYMENT.md](DEPLOYMENT.md).

## Velocity Configuration

Generated path:

- `plugins/mcggrtp/config.yml`

Important settings:

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
  server-1:
    display-name: "&aServer 1"
    enabled: true
  server-2:
    display-name: "&aServer 2"
    enabled: true

dimensions:
  overworld:
    servers: ["server-1", "server-2"]
  nether:
    servers: ["server-1"]
  end:
    servers: ["server-1"]
```

Velocity `[servers]` names must match McggRTP server ids:

```toml
[servers]
server-1 = "127.0.0.1:25566"
server-2 = "127.0.0.1:25567"
try = ["server-1"]
```

## Paper Configuration

Generated paths:

- `plugins/McggRTP-Paper/config.yml`
- `plugins/McggRTP-Paper/messages.yml`

Each backend must set its own Velocity server id:

```yml
network:
  current-server: "server-1"
```

On the `server-2` backend, use:

```yml
network:
  current-server: "server-2"
```

The `network.current-server` value must match the server name configured in Velocity.

The generated defaults intentionally describe a small two-server network. Add more server ids only after they exist in Velocity and in every relevant Paper config.

Production RTP concurrency is controlled here:

```yml
rtp:
  cooldown-seconds: 300
  max-concurrent-searches: 4
  adaptive-throttle:
    enabled: true
    min-concurrent-searches: 1
    min-tps: 18.5
    max-mspt: 80.0
    queue-start-delay-ticks: 2
    metrics-log-interval: 25
  location-pool:
    enabled: true
    target-size: 4
    refill-interval-ticks: 200
    max-refill-attempts: 4
    allow-generate-new-chunks: false
```

`max-concurrent-searches` limits how many RTP searches and chunk loads can run at the same time on a backend. Extra RTP requests wait in an in-memory queue and receive the `search-queued` message. Start with the conservative default, then raise it after watching TPS/MSPT on your hardware.

`adaptive-throttle` lets Paper reduce active RTP searches when backend health drops below the configured TPS/MSPT thresholds, then gradually recover back to `max-concurrent-searches`. When `debug.enabled` is true, McggRTP logs queue wait, search duration, attempt count, generated-chunk ratio, and current adaptive limit every `metrics-log-interval` completed RTP jobs.

`location-pool` precomputes safe destinations while the RTP queue is idle. By default it only reuses already-generated chunks, so it will not create new terrain in the background or compete with active player RTP requests. Set `allow-generate-new-chunks: true` only if you explicitly want the pool to pregenerate terrain during idle time.

## Velocity Forwarding

In `velocity.toml`:

```toml
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
```

In each Paper backend `config/paper-global.yml`:

```yml
proxies:
  velocity:
    enabled: true
    online-mode: true
    secret: "your-forwarding-secret"
```

In each Paper backend `server.properties`:

```properties
online-mode=false
```

The Paper secret must match Velocity's `forwarding.secret`.

## Permissions

Default permissions:

- `mcggrtp.use`
- `mcggrtp.dimension.overworld`
- `mcggrtp.dimension.nether`
- `mcggrtp.dimension.end`
- `mcggrtp.server.<server-id>`
- `mcggrtp.bypass.cooldown`
- `mcggrtp.admin.reload`

Normal RTP permissions default to allowed. Dynamic server permissions also default to allowed for normal players, so a basic install does not require players to be op. Admin and cooldown bypass permissions default to op-only.

Server permissions are dynamic. If a server entry omits `permission`, McggRTP derives it from:

```text
<server-permission-prefix><server-id>
```

Examples:

- `server-2` with prefix `mcggrtp.server.` becomes `mcggrtp.server.server-2`
- `skyblock321` with prefix `mcgg.server.` becomes `mcgg.server.skyblock321`

Use LuckPerms to deny or grant specific `mcggrtp.server.<server-id>` permissions when you want per-server RTP access control.

Recommended LuckPerms examples:

```text
/lp group default permission set mcggrtp.use true
/lp group default permission set mcggrtp.server.server-2 false
/lp group vip permission set mcggrtp.server.server-2 true
/lp group admin permission set mcggrtp.admin.reload true
```

## Commands

```text
/rtp
```

Opens the RTP GUI.

```text
/rtp reload
```

Reloads Paper config/messages and asks Velocity to reload its config. Requires `mcggrtp.admin.reload`.

## Debugging

Enable debug logs on both sides when diagnosing RTP issues:

```yml
debug:
  enabled: true
```

Debug mode logs:

- GUI and server-status requests
- cooldown checks
- pending RTP checks
- cross-server transfer results
- RTP search timing
- chunk loading
- destination validation
- teleport completion

Turn debug mode off on production once the issue is resolved unless you need verbose tracing.

## Validation

The project includes unit tests, targeted listener tests, and disposable live-network validation.

Run the full build and test suite:

```bash
GRADLE_USER_HOME=/tmp/mcggrtp-gradle-home ./gradlew build
```

Run live validation with a disposable Velocity plus two Paper servers:

```bash
python3 integration/run_live_validation.py
```

The integration harness expects local Paper/Velocity bootstrap artifacts under the paths managed by the scripts. It is intended for project development, not for production servers.

Install Node dependencies before running Mineflayer stress scripts for the first time:

```bash
cd integration
npm install
cd ..
```

Run local RTP stress validation:

```bash
MCGGRTP_STRESS_BOT_COUNT=50 MCGGRTP_STRESS_MODE=local python3 integration/run_stress_validation.py
```

Run cross-server RTP stress validation:

```bash
MCGGRTP_STRESS_MODE=cross MCGGRTP_STRESS_BOT_COUNT=10 python3 integration/run_stress_validation.py
```

For cross-server stress, the harness staggers the server-click phase by default. This avoids a known Mineflayer/Velocity protocol artifact where many simultaneous bot transfers can emit malformed configuration-state packets. The goal is to keep stress validation focused on McggRTP behavior instead of bot protocol noise.

If you need detailed Velocity packet decode logs during harness diagnosis:

```bash
MCGGRTP_VELOCITY_PACKET_DECODE_LOGGING=true MCGGRTP_STRESS_MODE=cross MCGGRTP_STRESS_BOT_COUNT=10 python3 integration/run_stress_validation.py
```

Recent local stress evidence:

- `botCount=50`
- `successCount=50`
- `failureCount=0`

Recent strict cross-server stress evidence:

- `botCount=10`
- `successCount=10`
- `failureCount=0`
- cross-server success required the destination message `[S2] Teleported`

The generated report is written to:

- `.integration/runtime/stress-report.json`

Validation evidence reference:

- Date: `2026-05-18`
- Branch: `dev`
- Commit: `6093276` for strict cross-server harness validation
- Artifact path: `.integration/runtime/stress-report.json`

## Constraints

- McggRTP is designed for Velocity plus Paper. It is not a standalone single-jar Bukkit plugin.
- Paper backends cannot reliably infer their Velocity server id, so `network.current-server` must be configured on every backend.
- Cross-server RTP requires both plugins and matching `mcggrtp:main` channel registration.
- Same-server RTP still depends on Velocity for network-wide cooldown ownership.
- Existing Paper configs are updated by merging missing default keys only; current values are not overwritten.
- Broad RTP into new terrain can still cost server resources because Minecraft chunk generation is expensive. Use reasonable radius, attempt, and concurrency settings.
- Mineflayer is useful for repeatable stress validation, but it is not a perfect vanilla-client substitute during heavy simultaneous cross-server transfers. Use server-side audit logs and real-client checks before treating bot-only transfer errors as production player failures.

## Troubleshooting

If `/rtp` does not show up, confirm the Paper jar is installed and check the Paper console for plugin startup errors.

If `/rtp` opens the GUI but cross-server RTP does not finish, confirm:

- both plugins are installed
- both sides use `mcggrtp:main`
- Velocity server ids match Paper `network.current-server`
- Velocity can reach the target backend
- debug mode is enabled while collecting logs
- the target Paper backend logs the player join and RTP completion

If stress validation reports Velocity `ClientSettingsPacket`, `Bad VarInt`, or packet decode errors during cross-server tests:

- first rerun with the default cross-server stagger
- reduce `MCGGRTP_STRESS_BOT_COUNT` to isolate plugin behavior from bot transfer noise
- enable `MCGGRTP_VELOCITY_PACKET_DECODE_LOGGING=true` only while diagnosing
- compare with a real client through Velocity before treating it as a production defect

If servers show the wrong status in the GUI, confirm:

- the server exists in Velocity's `[servers]` block
- the server exists in Velocity McggRTP `servers`
- the server is listed under the selected dimension
- the backend is actually reachable from the proxy

## License

McggRTP is licensed under the MIT License. See [LICENSE](LICENSE).
