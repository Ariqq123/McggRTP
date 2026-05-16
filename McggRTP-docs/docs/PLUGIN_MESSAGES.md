# McggRTP Plugin Messaging

Use one plugin message channel for communication between Paper and Velocity.

```txt
mcggrtp:main
```

## Message types

### CREATE_PENDING_RTP

Sent by Paper to Velocity when a player chooses a different server.

Fields:

```txt
requestId
playerUuid
targetServer
targetWorld
dimension
```

Velocity should:

```txt
Store pending RTP
Send player to targetServer
```

### CHECK_PENDING_RTP

Sent by Paper to Velocity when a player joins a backend server.

Fields:

```txt
playerUuid
currentServer
```

Velocity should:

```txt
Check if player has pending RTP for currentServer
Reply with PENDING_RTP_RESPONSE
```

### PENDING_RTP_RESPONSE

Sent by Velocity to Paper.

Fields:

```txt
hasPending
targetWorld
dimension
requestId
```

If `hasPending` is true, Paper starts RTP in `targetWorld`.

### CLEAR_PENDING_RTP

Sent by Paper to Velocity after teleport succeeds or fails.

Fields:

```txt
requestId
playerUuid
```

### RTP_RESULT

Sent by Paper to Velocity after attempting RTP.

Fields:

```txt
requestId
playerUuid
success
reason
```

## Suggested Java records

```java
package me.mcgg.azreyzaako.mcggrtp.common;

import java.util.UUID;

public record PendingRtp(
    UUID playerUuid,
    String targetServer,
    String targetWorld,
    String dimension,
    long createdAt
) {}
```

```java
package me.mcgg.azreyzaako.mcggrtp.common;

public enum MessageType {
    CREATE_PENDING_RTP,
    CHECK_PENDING_RTP,
    PENDING_RTP_RESPONSE,
    CLEAR_PENDING_RTP,
    RTP_RESULT
}
```

## Encoding recommendation

For MVP, use a simple byte stream:

```txt
writeUTF(messageType)
writeUTF(requestId)
writeUTF(playerUuid)
writeUTF(targetServer)
writeUTF(targetWorld)
writeUTF(dimension)
```

Later, you can switch to JSON or a small binary codec if the messages become more complex.
