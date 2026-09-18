package com.herasgarden.gardencore.util;

import com.herasgarden.gardencore.api.ui.GardenMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

public final class Messages {
    public static final String PREFIX_TEXT = GardenMessages.PREFIX_TEXT;
    public static final TextColor PREFIX_COLOR = GardenMessages.PREFIX_COLOR;
    public static final TextColor MESSAGE_COLOR = TextColor.color(0xE7E3E5);

    private Messages() {
    }

    public static Component prefix() {
        return GardenMessages.prefix();
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(prefix().append(Component.text(message, MESSAGE_COLOR)));
    }
}
