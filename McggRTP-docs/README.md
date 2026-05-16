# McggRTP

**McggRTP** is a network random teleport plugin designed for Velocity networks with Paper backend servers.

Author: `azreyzaako`  
Base package: `me.mcgg.azreyzaako.mcggrtp`

Suggested output names:

- `McggRTP-velocity.jar`
- `McggRTP-paper.jar`

## Goal

Players run `/rtp`, then a GUI opens:

1. Choose a dimension:
   - Overworld
   - Nether
   - The End
2. Choose a server for that dimension.
3. If the player is already on that server, RTP starts immediately.
4. If the player chose another server, Velocity transfers them there and the target Paper server performs RTP.

## Recommended modules

```txt
McggRTP/
  common/
  velocity/
  paper/
```

Recommended packages:

```txt
me.mcgg.azreyzaako.mcggrtp.common
me.mcgg.azreyzaako.mcggrtp.velocity
me.mcgg.azreyzaako.mcggrtp.paper
```

Recommended main classes:

```txt
me.mcgg.azreyzaako.mcggrtp.velocity.McggRTPVelocity
me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper
```

## Responsibilities

### Velocity plugin

Velocity should handle:

- Server switching
- Pending RTP requests
- Cooldowns
- Network-wide permissions if needed
- Server online/player count info
- Plugin messages between Paper servers

### Paper plugin

Paper should handle:

- `/rtp` command
- Inventory GUI
- Inventory click handling
- World and block safety checks
- Actual teleporting

## First version MVP

Start with this first:

- `/rtp` opens GUI on Paper.
- Main GUI has Overworld, Nether, and The End.
- Clicking a dimension opens a server submenu.
- Clicking a server either:
  - RTPs immediately if player is already there, or
  - asks Velocity to transfer the player and store pending RTP data.
- Target Paper server checks pending RTP on join and teleports the player.

Do not add economy, Redis, database storage, or claim-plugin checks until the basic flow works.
