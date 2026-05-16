# McggRTP Development Workflow

This is the recommended order for building McggRTP.

## Phase 1: Project setup

Create a multi-module Gradle project:

```txt
McggRTP/
  settings.gradle.kts
  build.gradle.kts
  common/build.gradle.kts
  velocity/build.gradle.kts
  paper/build.gradle.kts
```

Use Java 21 for modern Paper and Velocity development.

Recommended artifact names:

```txt
McggRTP-velocity.jar
McggRTP-paper.jar
```

Recommended package root:

```txt
me.mcgg.azreyzaako.mcggrtp
```

## Phase 2: Common module

Create shared message objects and codec utilities.

Suggested classes:

```txt
common/
  MessageType.java
  PendingRtp.java
  RtpRequest.java
  RtpResult.java
  MessageCodec.java
```

Suggested channel:

```txt
mcggrtp:main
```

Message types for MVP:

```txt
CREATE_PENDING_RTP
CHECK_PENDING_RTP
PENDING_RTP_RESPONSE
CLEAR_PENDING_RTP
RTP_RESULT
```

## Phase 3: Paper command and GUI

Create `/rtp` on the Paper plugin first.

Suggested classes:

```txt
paper/
  McggRTPPaper.java
  command/RtpCommand.java
  gui/RtpMainMenu.java
  gui/RtpServerMenu.java
  listener/RtpGuiListener.java
```

Expected behavior:

```txt
/rtp
↓
Open main dimension menu
↓
Click Overworld, Nether, or The End
↓
Open server submenu
↓
Click target server
```

## Phase 4: Velocity pending request system

Create a pending RTP manager on Velocity.

Suggested classes:

```txt
velocity/
  McggRTPVelocity.java
  manager/PendingRtpManager.java
  manager/CooldownManager.java
  messaging/VelocityMessageListener.java
  server/ServerTransferService.java
```

When a Paper server sends `CREATE_PENDING_RTP`, Velocity stores:

```txt
player UUID
target server
target world
dimension
created time
```

Then Velocity sends the player to the target server.

## Phase 5: Target-server RTP

When the player joins a Paper server:

```txt
PlayerJoinEvent
↓
Paper asks Velocity: CHECK_PENDING_RTP
↓
Velocity replies with target world if request exists
↓
Paper finds safe location
↓
Paper teleports player
↓
Paper sends RTP_RESULT
↓
Velocity clears pending request
```

## Phase 6: Safe location finder

Create a safe location service on Paper.

Suggested classes:

```txt
paper/
  rtp/SafeLocationFinder.java
  rtp/RtpTeleportService.java
  rtp/RtpSettings.java
```

Overworld checks:

```txt
Block below is solid
Feet block is passable
Head block is passable
Block below is not lava, fire, cactus, magma, etc.
Biome is not blacklisted
Location is inside world border
```

Nether checks need extra care:

```txt
Avoid lava
Avoid bedrock roof
Avoid inside blocks
Avoid tiny caves if desired
```

## Phase 7: Cooldowns and permissions

Add after the basic RTP flow works.

Recommended permissions:

```txt
rtp.use
rtp.dimension.overworld
rtp.dimension.nether
rtp.dimension.end
rtp.server.survival1
rtp.server.survival2
rtp.bypass.cooldown
rtp.admin.reload
```

Cooldowns should be stored on Velocity so players cannot bypass cooldowns by switching servers.

## Phase 8: Polish

Add these after MVP:

```txt
Configurable GUI items
Configurable messages
Server player counts in lore
Offline server display as barrier item
Reload command
Sound effects
Particle effects after RTP
Database or Redis support
Claim plugin hook
Economy cost
```
