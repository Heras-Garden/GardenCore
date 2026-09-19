package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCommand;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class AdminClaimCommand implements CommandExecutor, TabCompleter {
    private final GardenCommand delegate;

    public AdminClaimCommand(GardenCommand delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("gardencore.claim.admin")) {
            Messages.send(sender, "You do not have permission to use admin claims.");
            return true;
        }
        return delegate.onCommand(sender, command, label, normalize(args));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("gardencore.claim.admin")) return List.of();
        List<String> result = delegate.onTabComplete(sender, command, alias, normalize(args));
        return result == null ? List.of() : result;
    }

    private String[] normalize(String[] args) {
        if (args.length == 0) return new String[]{"claim", "start", "protected"};
        if (args.length == 1 && args[0].equalsIgnoreCase("start")) {
            return new String[]{"claim", "start", "protected"};
        }
        String[] normalized = new String[args.length + 1];
        normalized[0] = "claim";
        System.arraycopy(args, 0, normalized, 1, args.length);
        return normalized;
    }
}
