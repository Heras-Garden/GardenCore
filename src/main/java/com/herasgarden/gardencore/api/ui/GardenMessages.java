package com.herasgarden.gardencore.api.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Shared player-facing Garden SMP message formatting and locked brand palette. */
public final class GardenMessages {
    public static final String PREFIX_TEXT = "[Server] ";
    public static final TextColor PREFIX_COLOR = TextColor.color(0xAAAAAA);
    public static final TextColor MESSAGE_COLOR = TextColor.color(0xFFFFFF);

    public static final TextColor BUBBLEGUM_PINK = TextColor.color(0xFF6476);
    public static final TextColor PETAL_FROST = TextColor.color(0xFCD6DF);
    public static final TextColor PALE_SKY = TextColor.color(0xC4DDF2);
    public static final TextColor TUSCAN_SUN = TextColor.color(0xF9C349);
    public static final TextColor MUTED_OLIVE = TextColor.color(0xA3B565);

    private GardenMessages() {
    }

    public static Component prefix() {
        return Component.text(PREFIX_TEXT, PREFIX_COLOR);
    }

    public static Component message(String message) {
        return prefix().append(Component.text(message == null ? "" : message, MESSAGE_COLOR));
    }

    public static Component success(String message) {
        return prefix().append(Component.text(message == null ? "" : message, MUTED_OLIVE));
    }

    public static Component info(String message) {
        return prefix().append(Component.text(message == null ? "" : message, PALE_SKY));
    }

    public static Component warning(String message) {
        return prefix().append(Component.text(message == null ? "" : message, TUSCAN_SUN));
    }

    public static Component error(String message) {
        return prefix().append(Component.text(message == null ? "" : message, BUBBLEGUM_PINK));
    }

    public static Component action(String label, String command, String hover, TextColor color) {
        Component component = Component.text(label == null ? "" : label, color == null ? PALE_SKY : color)
                .decorate(TextDecoration.BOLD);
        if (command != null && !command.isBlank()) component = component.clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isBlank()) component = component.hoverEvent(HoverEvent.showText(Component.text(hover)));
        return component;
    }

    public static Component statusCard(String title, List<Component> lines, Component action) {
        Component card = Component.text(title == null ? "" : title.toUpperCase(), TUSCAN_SUN)
                .decorate(TextDecoration.BOLD);
        if (lines != null) {
            for (Component line : lines) card = card.append(Component.newline()).append(line);
        }
        if (action != null) card = card.append(Component.newline()).append(Component.newline()).append(action);
        return card;
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(message(message));
    }
}
