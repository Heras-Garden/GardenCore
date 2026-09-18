package com.herasgarden.gardencore.property;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.util.Messages;
import com.herasgarden.gardencore.worldguard.WorldGuardHook;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class PropertyCommand implements CommandExecutor, TabCompleter {
    private final GardenCore plugin;
    private final PropertyRegistry registry;
    private final WorldGuardHook worldGuard;

    public PropertyCommand(GardenCore plugin, PropertyRegistry registry, WorldGuardHook worldGuard) {
        this.plugin = plugin;
        this.registry = registry;
        this.worldGuard = worldGuard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("property")) {
            Messages.send(sender, "Use /garden property <create|info|mailbox|list>.");
            return true;
        }
        if (args.length < 2) {
            Messages.send(sender, "Use /garden property <create|info|mailbox|list>.");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "create" -> create(sender, args);
            case "info" -> info(sender, args);
            case "mailbox" -> mailbox(sender, args);
            case "list" -> list(sender);
            default -> {
                Messages.send(sender, "Use /garden property <create|info|mailbox|list>.");
                yield true;
            }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gardencore.property.admin")) {
            Messages.send(sender, "You do not have permission to create properties.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Create properties in-game so GardenCore knows which world to use.");
            return true;
        }
        if (args.length < 6) {
            Messages.send(sender, "Use /garden property create <region> <number> <price> <road...>.");
            return true;
        }

        String regionId = args[2];
        String number = args[3];
        Long price = parsePrice(args[4]);
        String road = join(args, 5);
        if (price == null || road.isBlank() || number.isBlank()) {
            Messages.send(sender, "The road, number, and price must be valid.");
            return true;
        }

        if (!worldGuard.isAvailable()) {
            Messages.send(sender, "WorldGuard must be installed before properties can be created.");
            return true;
        }
        if (!worldGuard.regionExists(player.getWorld(), regionId)) {
            Messages.send(sender, "WorldGuard region '" + regionId + "' was not found in this world.");
            return true;
        }
        if (registry.get(road, number) != null) {
            Messages.send(sender, number + " " + road + " already exists.");
            return true;
        }

        Property property = new Property(road, number, player.getWorld().getName(), regionId, price);
        registry.add(property);
        if (!registry.saveQuietly()) {
            Messages.send(sender, "The property could not be saved.");
            return true;
        }

        Messages.send(sender, "Created " + number + " " + road + " for ⟡ " + price + ". Place its three-line sign to bind it.");
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 4) {
            Messages.send(sender, "Use /garden property info <number> <road...>.");
            return true;
        }
        String number = args[2];
        String road = join(args, 3);
        Property property = registry.get(road, number);
        if (property == null) {
            Messages.send(sender, "That property was not found.");
            return true;
        }

        String owner = property.ownerName() == null ? "None" : property.ownerName();
        Messages.send(sender, property.number() + " " + property.road()
                + " | Region: " + property.regionId()
                + " | Owner: " + owner
                + " | Price: ⟡ " + property.price()
                + " | For sale: " + property.forSale());
        return true;
    }

    private boolean mailbox(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "This command must be used in-game.");
            return true;
        }
        if (args.length < 4) {
            Messages.send(sender, "Look at a chest, then use /garden property mailbox <number> <road...>.");
            return true;
        }

        String number = args[2];
        String road = join(args, 3);
        Property property = registry.get(road, number);
        if (property == null) {
            Messages.send(sender, "That property was not found.");
            return true;
        }

        boolean admin = player.hasPermission("gardencore.property.admin");
        boolean owner = property.ownerUuid() != null && property.ownerUuid().equals(player.getUniqueId());
        if (!admin && !owner) {
            Messages.send(sender, "You do not own this property.");
            return true;
        }

        Block target = player.getTargetBlockExact(6);
        if (target == null || (target.getType() != Material.CHEST && target.getType() != Material.TRAPPED_CHEST && target.getType() != Material.BARREL)) {
            Messages.send(sender, "Look directly at the chest or barrel you want to use as the mailbox.");
            return true;
        }

        property.setMailboxLocation(target.getLocation());
        if (!registry.saveQuietly()) {
            Messages.send(sender, "The mailbox could not be saved.");
            return true;
        }
        Messages.send(sender, "Mailbox registered for " + property.number() + " " + property.road() + ".");
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("gardencore.property.admin")) {
            Messages.send(sender, "You do not have permission to list all properties.");
            return true;
        }
        if (registry.size() == 0) {
            Messages.send(sender, "No properties have been created yet.");
            return true;
        }
        Messages.send(sender, "Properties: " + registry.size());
        for (Property property : registry.all()) {
            sender.sendMessage("- " + property.number() + " " + property.road()
                    + " | " + (property.ownerName() == null ? "For sale ⟡ " + property.price() : property.ownerName()));
        }
        return true;
    }

    private Long parsePrice(String input) {
        try {
            long price = Long.parseLong(input);
            return price > 0 ? price : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefix(List.of("property"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("property")) {
            return prefix(List.of("create", "info", "mailbox", "list"), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> prefix(List<String> options, String input) {
        String lower = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
