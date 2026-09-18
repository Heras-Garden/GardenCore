package com.herasgarden.gardencore;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Lets top-level aliases such as /claim behave exactly like /garden claim.
 */
public final class SubcommandAliasCommand implements CommandExecutor, TabCompleter {
    private final GardenCommand delegate;
    private final String root;

    public SubcommandAliasCommand(GardenCommand delegate, String root) {
        this.delegate = delegate;
        this.root = root;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return delegate.onCommand(sender, command, label, withRoot(args));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> results = delegate.onTabComplete(sender, command, alias, withRoot(args));
        return results == null ? List.of() : results;
    }

    private String[] withRoot(String[] args) {
        String[] normalized = new String[args.length + 1];
        normalized[0] = root;
        System.arraycopy(args, 0, normalized, 1, args.length);
        return normalized;
    }
}
