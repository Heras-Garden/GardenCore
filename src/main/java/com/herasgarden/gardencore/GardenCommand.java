package com.herasgarden.gardencore;

import com.herasgarden.gardencore.claim.ClaimCommand;
import com.herasgarden.gardencore.organization.CompanyCommand;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class GardenCommand implements CommandExecutor, TabCompleter {
    private final ClaimCommand claimCommand;
    private final CompanyCommand companyCommand;

    public GardenCommand(ClaimCommand claimCommand, CompanyCommand companyCommand) {
        this.claimCommand = claimCommand;
        this.companyCommand = companyCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            Messages.send(sender, "Use /claim or /company. Property commands are provided by GardenLands as /property.");
            return true;
        }
        if (args[0].equalsIgnoreCase("claim")) {
            return claimCommand.handle(sender, command, label, args);
        }
        if (args[0].equalsIgnoreCase("company")) {
            return companyCommand.handle(sender, args);
        }
        if (args[0].equalsIgnoreCase("property")) {
            Messages.send(sender, "Use /property. Property commands are owned by GardenLands.");
            return true;
        }
        Messages.send(sender, "Use /claim or /company. Property commands are provided by GardenLands as /property.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return List.of("claim", "company").stream().filter(value -> value.startsWith(lower)).toList();
        }
        if (args[0].equalsIgnoreCase("claim")) {
            return claimCommand.tabComplete(sender, args);
        }
        if (args[0].equalsIgnoreCase("company")) {
            return companyCommand.tabComplete(args);
        }
        return List.of();
    }
}
