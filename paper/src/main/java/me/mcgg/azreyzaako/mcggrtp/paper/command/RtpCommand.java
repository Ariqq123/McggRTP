package me.mcgg.azreyzaako.mcggrtp.paper.command;

import me.mcgg.azreyzaako.mcggrtp.paper.McggRTPPaper;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RtpCommand implements CommandExecutor, BasicCommand {
    private final McggRTPPaper plugin;

    public RtpCommand(McggRTPPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    @Override
    public void execute(CommandSourceStack commandSourceStack, String[] args) {
        handle(commandSourceStack.getSender(), args);
    }

    @Override
    public String permission() {
        return "rtp.use";
    }

    private boolean handle(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            if (!player.hasPermission("rtp.admin.reload")) {
                player.sendMessage(plugin.messages().text("no-permission"));
                return true;
            }

            plugin.reloadPluginState();
            player.sendMessage(plugin.messages().text("reload-local"));
            player.sendMessage(plugin.messages().text("reload-started"));
            plugin.messageBridge().sendReloadConfig(player);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("rtp.use")) {
            player.sendMessage(plugin.messages().text("no-permission"));
            return true;
        }

        plugin.messageBridge().openMainMenu(player);
        return true;
    }
}
