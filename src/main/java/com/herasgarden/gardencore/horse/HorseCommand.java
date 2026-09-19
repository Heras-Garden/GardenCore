package com.herasgarden.gardencore.horse;

import com.herasgarden.gardencore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class HorseCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final HorseAccessListener access;

    public HorseCommand(JavaPlugin plugin, HorseAccessListener access) {
        this.plugin = plugin;
        this.access = access;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Horse access commands must be used in-game.");
            return true;
        }
        AbstractHorse horse = target(player);
        if (horse == null) {
            Messages.send(player, "Look directly at your tamed horse within 6 blocks.");
            return true;
        }
        if (!access.owns(player, horse) && !player.hasPermission("gardencore.horse.admin")) {
            Messages.send(player, "You do not own that horse.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            Messages.send(player, "Horse access: " + (access.publicAccess(horse) ? "public" : "private")
                    + " | trusted players: " + access.trusted(horse).size() + ".");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "access" -> {
                if (args.length < 2 || (!args[1].equalsIgnoreCase("public") && !args[1].equalsIgnoreCase("private"))) {
                    Messages.send(player, "Use /horse access <public|private>.");
                    return true;
                }
                boolean publicAccess = args[1].equalsIgnoreCase("public");
                access.setPublic(horse, publicAccess);
                Messages.send(player, "Horse access is now " + (publicAccess ? "public." : "private."));
            }
            case "trust", "untrust" -> {
                if (args.length < 2) {
                    Messages.send(player, "Use /horse " + args[0].toLowerCase(Locale.ROOT) + " <player>.");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && !target.isOnline()) {
                    Messages.send(player, "That player has not joined the server before.");
                    return true;
                }
                Set<UUID> trusted = access.trusted(horse);
                if (args[0].equalsIgnoreCase("trust")) trusted.add(target.getUniqueId());
                else trusted.remove(target.getUniqueId());
                access.setTrusted(horse, trusted);
                Messages.send(player, target.getName() + (args[0].equalsIgnoreCase("trust")
                        ? " can now ride this horse." : " can no longer ride this horse."));
            }
            default -> Messages.send(player, "Use /horse <info|access|trust|untrust>.");
        }
        return true;
    }

    private AbstractHorse target(Player player) {
        Entity target = player.getTargetEntity(6);
        return target instanceof AbstractHorse horse ? horse : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("info", "access", "trust", "untrust").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("access")) {
            return List.of("public", "private").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) names.add(player.getName());
            }
            return names;
        }
        return List.of();
    }
}
