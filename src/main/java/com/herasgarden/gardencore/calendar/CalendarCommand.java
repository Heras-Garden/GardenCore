package com.herasgarden.gardencore.calendar;

import com.herasgarden.gardencore.api.calendar.GardenCalendar;
import com.herasgarden.gardencore.api.ui.GardenMessages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class CalendarCommand implements CommandExecutor, TabCompleter {
    private final GardenCalendar calendar;

    public CalendarCommand(GardenCalendar calendar) {
        this.calendar = calendar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GardenCalendar.CalendarSnapshot snapshot = calendar.snapshot();
        if (args.length == 0) {
            boolean use24Hour = !(sender instanceof Player player)
                    || calendar.uses24HourTime(player.getUniqueId());
            GardenMessages.send(sender, snapshot.formattedTime(use24Hour) + ".");
            if (sender instanceof Player player) {
                GardenMessages.send(player, "Calendar HUD: "
                        + (calendar.hudEnabled(player.getUniqueId()) ? "ON" : "OFF")
                        + " | Time format: "
                        + (calendar.uses24HourTime(player.getUniqueId()) ? "24-hour" : "12-hour") + ".");
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            GardenMessages.send(sender, "Only players can change calendar display settings.");
            return true;
        }
        try {
            if (args[0].equalsIgnoreCase("hud")) {
                String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
                boolean current = calendar.hudEnabled(player.getUniqueId());
                boolean enabled = switch (mode) {
                    case "on" -> true;
                    case "off" -> false;
                    case "toggle" -> !current;
                    default -> throw new IllegalArgumentException("Use /calendar hud <on|off|toggle>.");
                };
                calendar.setHudEnabled(player.getUniqueId(), enabled);
                GardenMessages.send(player, "Calendar HUD " + (enabled ? "enabled" : "disabled") + ".");
                return true;
            }
            if (args[0].equalsIgnoreCase("format")) {
                if (args.length < 2 || (!args[1].equals("12") && !args[1].equals("24"))) {
                    throw new IllegalArgumentException("Use /calendar format <12|24>.");
                }
                boolean use24Hour = args[1].equals("24");
                calendar.setUses24HourTime(player.getUniqueId(), use24Hour);
                GardenMessages.send(player, "Calendar time format set to "
                        + (use24Hour ? "24-hour" : "12-hour") + ".");
                return true;
            }
            throw new IllegalArgumentException(
                    "Use /calendar, /calendar hud <on|off|toggle>, or /calendar format <12|24>.");
        } catch (IllegalArgumentException exception) {
            GardenMessages.send(player, exception.getMessage());
        } catch (SQLException exception) {
            GardenMessages.send(player, "Calendar preference could not be saved.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("hud", "format"));
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            return match(args[1], List.of("on", "off", "toggle"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("format")) {
            return match(args[1], List.of("12", "24"));
        }
        return List.of();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.startsWith(lower)).toList();
    }
}
