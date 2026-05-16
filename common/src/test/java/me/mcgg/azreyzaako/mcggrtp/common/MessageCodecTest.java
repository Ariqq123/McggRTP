package me.mcgg.azreyzaako.mcggrtp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageCodecTest {
    @Test
    void createPendingRequestRoundTrips() {
        RtpRequest request = new RtpRequest("req-1", UUID.randomUUID(), "survival-2", "world", "overworld");

        byte[] encoded = MessageCodec.encodeCreatePendingRtp(request);

        assertEquals(MessageType.CREATE_PENDING_RTP, MessageCodec.peekType(encoded));
        assertEquals(request, MessageCodec.decodeCreatePendingRtp(encoded));
    }

    @Test
    void cooldownResponseRoundTrips() {
        CooldownResponse response = new CooldownResponse("cooldown-1", true, 42);

        byte[] encoded = MessageCodec.encodeCooldownResponse(response);

        assertEquals(MessageType.COOLDOWN_RESPONSE, MessageCodec.peekType(encoded));
        assertEquals(response, MessageCodec.decodeCooldownResponse(encoded));
    }

    @Test
    void serverStatusResponseRoundTrips() {
        ServerStatusResponse response = new ServerStatusResponse(
                "status-1",
                "nether",
                List.of(
                        new ServerStatusEntry("survival-1", "&aSurvival 1", true, 4),
                        new ServerStatusEntry("survival-2", "&aSurvival 2", false, 0)
                )
        );

        byte[] encoded = MessageCodec.encodeServerStatusResponse(response);

        assertEquals(MessageType.SERVER_STATUS_RESPONSE, MessageCodec.peekType(encoded));
        ServerStatusResponse decoded = MessageCodec.decodeServerStatusResponse(encoded);
        assertEquals(response.requestId(), decoded.requestId());
        assertEquals(response.dimension(), decoded.dimension());
        assertEquals(response.entries(), decoded.entries());
    }

    @Test
    void commandAckRoundTrips() {
        CommandAck ack = new CommandAck("reload-1", false, "&cReload failed");

        byte[] encoded = MessageCodec.encodeCommandAck(ack);

        assertEquals(MessageType.COMMAND_ACK, MessageCodec.peekType(encoded));
        assertEquals(ack, MessageCodec.decodeCommandAck(encoded));
    }

    @Test
    void pendingResponseEmptyHelperUsesFalseState() {
        PendingRtpResponse empty = PendingRtpResponse.empty();

        assertFalse(empty.hasPending());
        assertTrue(empty.requestId().isEmpty());
        assertTrue(empty.targetWorld().isEmpty());
    }
}
