package com.herasgarden.gardencore.api.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

/**
 * Shared player-facing Garden SMP message formatting.
 *
 * Domain plugins should use this helper for ordinary server messages so the
 * Garden plugin family keeps one prefix and one baseline text treatment.
 */
public final class GardenMessages {
    public static final String PREFIX_TEXT = "[Server] ";
    public static final TextColor PREFIX_COLOR = TextColor.color(0xAAAAAA);
    public static final TextColor MESSAGE_COLOR = TextColor.color(0xFFFFFF);

    private GardenMessages() {
    }

    public static Component prefix() {
        return Component.text(PREFIX_TEXT, PREFIX_COLOR);
    }

    public static Component message(String message) {
        return prefix().append(Component.text(message == null ? "" : message, MESSAGE_COLOR));
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(message(message));
    }
}
