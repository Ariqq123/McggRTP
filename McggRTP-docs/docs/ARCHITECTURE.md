# McggRTP Architecture

McggRTP should be split into three modules:

```txt
common
velocity
paper
```

## Module purpose

### common

Shared code used by both Velocity and Paper.

Suggested package:

```txt
me.mcgg.azreyzaako.mcggrtp.common
```

Use this module for:

- Message types
- Request and response records
- Encoding and decoding plugin messages
- Shared constants

Example classes:

```txt
MessageType
PendingRtp
RtpRequest
RtpResult
MessageCodec
McggRTPChannels
```

### velocity

Proxy-side plugin.

Suggested main class:

```txt
me.mcgg.azreyzaako.mcggrtp.velocity.McggRTPVelocity
```

Use this module for:

- Pending RTP requests
- Cross-server transfer
- Cooldowns
- Network server lookup
- Plugin message response handling

Velocity does **not** open inventory GUIs and does **not** inspect blocks.

### paper

Backend-side plugin.

Suggested main class:

```txt
me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper
```

Use this module for:

- `/rtp` command
- Inventory GUI
- InventoryClickEvent handling
- PlayerJoinEvent pending RTP check
- Safe random location search
- Teleporting the player

Paper does **not** know the full network state by itself, so it asks Velocity for pending request and server-transfer data.

## Main RTP flow

```txt
Player runs /rtp on Paper
↓
Paper opens dimension GUI
↓
Player chooses Overworld, Nether, or The End
↓
Paper opens server submenu
↓
Player chooses a server
↓
Paper checks if selected server is current server
```

### If selected server is current server

```txt
Paper directly starts RTP in selected world
```

### If selected server is different

```txt
Paper sends CREATE_PENDING_RTP to Velocity
↓
Velocity stores request
↓
Velocity sends player to selected server
↓
Target Paper server sees player join
↓
Target Paper asks Velocity for CHECK_PENDING_RTP
↓
Velocity replies with PENDING_RTP_RESPONSE
↓
Target Paper performs RTP
↓
Paper sends RTP_RESULT
↓
Velocity clears pending request
```

## Why this split matters

Velocity can move players between backend servers, but it cannot safely inspect worlds or blocks.

Paper can inspect worlds and open inventory GUIs, but it cannot transfer players across the Velocity network by itself without asking the proxy.

That is why McggRTP needs both plugins.
