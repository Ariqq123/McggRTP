package me.mcgg.azreyzaako.mcggrtp.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MessageCodec {
    private MessageCodec() {
    }

    public static byte[] encodeCreatePendingRtp(RtpRequest request) {
        return write(MessageType.CREATE_PENDING_RTP, out -> {
            writeUtf(out, request.requestId());
            writeUuid(out, request.playerUuid());
            writeUtf(out, request.targetServer());
            writeUtf(out, request.targetWorld());
            writeUtf(out, request.dimension());
        });
    }

    public static RtpRequest decodeCreatePendingRtp(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.CREATE_PENDING_RTP);
            return new RtpRequest(
                    readUtf(input),
                    readUuid(input),
                    readUtf(input),
                    readUtf(input),
                    readUtf(input)
            );
        });
    }

    public static byte[] encodeCheckPendingRtp(UUID playerUuid, String currentServer) {
        return write(MessageType.CHECK_PENDING_RTP, out -> {
            writeUuid(out, playerUuid);
            writeUtf(out, currentServer);
        });
    }

    public static CheckPendingRtp decodeCheckPendingRtp(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.CHECK_PENDING_RTP);
            return new CheckPendingRtp(readUuid(input), readUtf(input));
        });
    }

    public static byte[] encodePendingRtpResponse(PendingRtpResponse response) {
        return write(MessageType.PENDING_RTP_RESPONSE, out -> {
            out.writeBoolean(response.hasPending());
            writeUtf(out, response.requestId());
            writeUtf(out, response.targetWorld());
            writeUtf(out, response.dimension());
        });
    }

    public static PendingRtpResponse decodePendingRtpResponse(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.PENDING_RTP_RESPONSE);
            return new PendingRtpResponse(
                    input.readBoolean(),
                    readUtf(input),
                    readUtf(input),
                    readUtf(input)
            );
        });
    }

    public static byte[] encodeClearPendingRtp(String requestId, UUID playerUuid) {
        return write(MessageType.CLEAR_PENDING_RTP, out -> {
            writeUtf(out, requestId);
            writeUuid(out, playerUuid);
        });
    }

    public static ClearPendingRtp decodeClearPendingRtp(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.CLEAR_PENDING_RTP);
            return new ClearPendingRtp(readUtf(input), readUuid(input));
        });
    }

    public static byte[] encodeRtpResult(RtpResult result) {
        return write(MessageType.RTP_RESULT, out -> {
            writeUtf(out, result.requestId());
            writeUuid(out, result.playerUuid());
            out.writeBoolean(result.success());
            writeUtf(out, result.reason());
        });
    }

    public static RtpResult decodeRtpResult(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.RTP_RESULT);
            return new RtpResult(
                    readUtf(input),
                    readUuid(input),
                    input.readBoolean(),
                    readUtf(input)
            );
        });
    }

    public static byte[] encodeCheckCooldown(CooldownCheck check) {
        return write(MessageType.CHECK_COOLDOWN, out -> {
            writeUtf(out, check.requestId());
            writeUuid(out, check.playerUuid());
        });
    }

    public static CooldownCheck decodeCheckCooldown(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.CHECK_COOLDOWN);
            return new CooldownCheck(readUtf(input), readUuid(input));
        });
    }

    public static byte[] encodeCooldownResponse(CooldownResponse response) {
        return write(MessageType.COOLDOWN_RESPONSE, out -> {
            writeUtf(out, response.requestId());
            out.writeBoolean(response.active());
            out.writeLong(response.remainingSeconds());
        });
    }

    public static CooldownResponse decodeCooldownResponse(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.COOLDOWN_RESPONSE);
            return new CooldownResponse(readUtf(input), input.readBoolean(), input.readLong());
        });
    }

    public static byte[] encodeServerStatusRequest(ServerStatusRequest request) {
        return write(MessageType.REQUEST_SERVER_STATUS, out -> {
            writeUtf(out, request.requestId());
            writeUuid(out, request.playerUuid());
            writeUtf(out, request.dimension());
        });
    }

    public static ServerStatusRequest decodeServerStatusRequest(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.REQUEST_SERVER_STATUS);
            return new ServerStatusRequest(readUtf(input), readUuid(input), readUtf(input));
        });
    }

    public static byte[] encodeServerStatusResponse(ServerStatusResponse response) {
        return write(MessageType.SERVER_STATUS_RESPONSE, out -> {
            writeUtf(out, response.requestId());
            writeUtf(out, response.dimension());
            out.writeInt(response.entries().size());
            for (ServerStatusEntry entry : response.entries()) {
                writeUtf(out, entry.serverId());
                writeUtf(out, entry.displayName());
                out.writeBoolean(entry.online());
                out.writeInt(entry.playerCount());
            }
        });
    }

    public static ServerStatusResponse decodeServerStatusResponse(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.SERVER_STATUS_RESPONSE);
            String requestId = readUtf(input);
            String dimension = readUtf(input);
            int count = input.readInt();
            java.util.List<ServerStatusEntry> entries = new java.util.ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                entries.add(new ServerStatusEntry(
                        readUtf(input),
                        readUtf(input),
                        input.readBoolean(),
                        input.readInt()
                ));
            }
            return new ServerStatusResponse(requestId, dimension, java.util.List.copyOf(entries));
        });
    }

    public static byte[] encodeReloadConfig(ReloadConfigRequest request) {
        return write(MessageType.RELOAD_CONFIG, out -> {
            writeUtf(out, request.requestId());
            writeUuid(out, request.playerUuid());
        });
    }

    public static ReloadConfigRequest decodeReloadConfig(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.RELOAD_CONFIG);
            return new ReloadConfigRequest(readUtf(input), readUuid(input));
        });
    }

    public static byte[] encodeCommandAck(CommandAck ack) {
        return write(MessageType.COMMAND_ACK, out -> {
            writeUtf(out, ack.requestId());
            out.writeBoolean(ack.success());
            writeUtf(out, ack.message());
        });
    }

    public static CommandAck decodeCommandAck(byte[] payload) {
        return read(payload, input -> {
            expectType(input, MessageType.COMMAND_ACK);
            return new CommandAck(readUtf(input), input.readBoolean(), readUtf(input));
        });
    }

    public static MessageType peekType(byte[] payload) {
        return read(payload, input -> MessageType.valueOf(readUtf(input)));
    }

    private static byte[] write(MessageType type, IOConsumer<DataOutputStream> consumer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeUtf(output, type.name());
            consumer.accept(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static <T> T read(byte[] payload, IOFunction<DataInputStream, T> function) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            return function.apply(input);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void expectType(DataInputStream input, MessageType expected) throws IOException {
        MessageType actual = MessageType.valueOf(readUtf(input));
        if (actual != expected) {
            throw new IOException("Unexpected message type " + actual + ", expected " + expected);
        }
    }

    private static void writeUtf(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readUtf(DataInputStream input) throws IOException {
        int length = input.readInt();
        byte[] bytes = input.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUuid(DataOutputStream output, UUID uuid) throws IOException {
        writeUtf(output, uuid.toString());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return UUID.fromString(readUtf(input));
    }

    @FunctionalInterface
    private interface IOConsumer<T> {
        void accept(T value) throws IOException;
    }

    @FunctionalInterface
    private interface IOFunction<T, R> {
        R apply(T value) throws IOException;
    }

    public record CheckPendingRtp(UUID playerUuid, String currentServer) {
    }

    public record ClearPendingRtp(String requestId, UUID playerUuid) {
    }
}
