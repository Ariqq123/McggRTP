package me.mcgg.azreyzaako.mcggrtp.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RtpCommandTest {
    private ServerMock server;
    private McggRTPPaper plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(McggRTPPaper.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rtpCommandReachesGuiOpenPath() {
        PlayerMock player = server.addPlayer("runner");
        RtpCommand command = new RtpCommand(plugin);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> command.onCommand(player, mock(Command.class), "rtp", new String[0]));

        assertTrue(exception instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException);
    }

    @Test
    void reloadRequiresAdminPermission() {
        PlayerMock player = server.addPlayer("runner");
        player.addAttachment(plugin, "rtp.admin.reload", false);
        RtpCommand command = new RtpCommand(plugin);

        boolean handled = command.onCommand(player, mock(Command.class), "rtp", new String[] {"reload"});

        assertTrue(handled);
        assertEquals("§8[§aMcggRTP§8] §cYou do not have permission.", player.nextMessage());
    }
}
