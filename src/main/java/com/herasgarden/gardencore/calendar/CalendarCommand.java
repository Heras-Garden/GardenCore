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
            GardenMessages.send(sender, snapshot.formattedTime() + ".");
            if (sender instanceof Player player) {
                try {
                    GardenMessages.send(player, "Calendar HUD: "
                            + (calendar.hudEnabled(player.getUniqueId()) ? "ON" : "OFF")
                            + ". Use /calendar hud toggle.");
                } catch (SQLException exception) {
                    GardenMessages.send(player, "Calendar HUD preference could not be read.");
                }
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            GardenMessages.send(sender, "Only players can change calendar HUD settings.");
            return true;
        }
        if (!args[0].equalsIgnoreCase("hud")) {
            GardenMessages.send(player, "Use /calendar or /calendar hud <on|off|toggle>.");
            return true;
        }

        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "toggle";
        try {
            boolean current = calendar.hudEnabled(player.getUniqueId());
            boolean enabled = switch (mode) {
                case "on" -> true;
                case "off" -> false;
                case "toggle" -> !current;
                default -> throw new IllegalArgumentException("Use /calendar hud <on|off|toggle>.");
            };
            calendar.setHudEnabled(player.getUniqueId(), enabled);
            GardenMessages.send(player, "Calendar HUD " + (enabled ? "enabled" : "disabled") + ".");
        } catch (IllegalArgumentException exception) {
            GardenMessages.send(player, exception.getMessage());
        } catch (SQLException exception) {
            GardenMessages.send(player, "Calendar HUD preference could not be saved.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("hud"));
        if (args.length == 2 && args[0].equalsIgnoreCase("hud")) {
            return match(args[1], List.of("on", "off", "toggle"));
        }
        return List.of();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.startsWith(lower)).toList();
    }
}
