package com.herasgarden.gardencore.ui;

import com.herasgarden.gardencore.claim.*;
import com.herasgarden.gardencore.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class ClaimChatUi {
    private static final TextColor PREFIX = Messages.PREFIX_COLOR;
    private static final TextColor TEXT = TextColor.color(0xFFFFFF);
    private static final TextColor MUTED = TextColor.color(0xD6D1D4);
    private static final TextColor ACCENT = TextColor.color(0xF2AFC8);
    private static final TextColor POSITIVE = TextColor.color(0xA8E6A3);
    private static final TextColor NEGATIVE = TextColor.color(0xFF9191);

    private ClaimChatUi() {}

    public static void sendSelectionControls(Player player, ClaimSession session, ClaimValidation validation) {
        TextComponent.Builder line = Component.text().append(prefix());
        if (session.closed()) {
            if (validation != null && validation.valid()) {
                line.append(button("Confirm", "/claim confirm", POSITIVE, "Create this claim."));
            }
            line.append(Component.space())
                    .append(button("Edit", "/claim edit", ACCENT, "Reopen the boundary."))
                    .append(Component.space())
                    .append(button("Undo Point", "/claim undo", ACCENT, "Remove the last corner."))
                    .append(Component.space())
                    .append(button("Settings", "/claim settings", ACCENT, "Claim preview settings."));
            if (session.type() == session.type() == ClaimType.DISTRICT) {
                line.append(Component.space())
                        .append(suggestButton("Set Name", "/claim name ", ACCENT,
                                "Set the district name before confirming."));
            }
            if (session.type() == session.type() == ClaimType.DISTRICT) {
                line.append(Component.space())
                        .append(suggestButton("Set Name", "/claim name ", ACCENT,
                                "Set the district name before confirming."));
            }
            if (session.type() == ClaimType.TERRITORY) {
                line.append(Component.space())
                        .append(button("Territory", "/claim territory status", ACCENT, "Set territory name and flag."));
            }
            line.append(Component.space())
                    .append(button("Cancel", "/claim cancel", NEGATIVE, "Discard this claim."));
        } else {
            line.append(button("Undo", "/claim undo", ACCENT, "Remove the last point."))
                    .append(Component.space())
                    .append(button("Settings", "/claim settings", ACCENT, "Claim preview settings."));
            if (session.type() == ClaimType.TERRITORY) {
                line.append(Component.space())
                        .append(button("Territory", "/claim territory status", ACCENT, "Set territory name and flag."));
            }
            line.append(Component.space())
                    .append(button("Cancel", "/claim cancel", NEGATIVE, "Discard this claim."));
        }
        player.sendMessage(line.build());
    }

    public static void sendPreviewSummary(Player player, ClaimSession session, ClaimGeometry geometry,
                                          ClaimValidation validation) {
        if (geometry == null) {
            return;
        }
        String dimension;
        if (session.shape() == ClaimShape.RECTANGLE) {
            dimension = geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) {
                dimension += " x " + geometry.height();
            }
        } else {
            dimension = geometry.vertices().size() + " points | bounds " + geometry.width() + " x " + geometry.length();
            if (!geometry.fullHeight()) {
                dimension += " x " + geometry.height();
            }
        }

        Component status = validation != null && !validation.valid()
                ? Component.text("Invalid: " + validation.reason(), NEGATIVE)
                : Component.text(session.closed() ? "Ready to confirm" : "Preview", POSITIVE);

        String name = session.claimName() == null || session.claimName().isBlank()
                ? "" : session.claimName() + " | ";
        player.sendMessage(prefix()
                .append(Component.text("Claim: " + name + dimension + " | "
                        + geometry.blockAreaEstimate() + " blocks² | ", TEXT))
                .append(status));
    }

    public static void sendSettings(Player player, ClaimSession session) {
        String height = session.fullHeight() ? "Full world" : session.minY() + " to " + session.maxY();
        player.sendActionBar(Component.text("Claim settings | Height: " + height, MUTED));

        TextComponent.Builder menu = Component.text()
                .append(prefix())
                .append(Component.text("Claim settings", TEXT))
                .append(Component.newline())
                .append(Component.text("Height: " + height, MUTED))
                .append(Component.newline());

        if (session.type() == ClaimType.UNIT) {
            menu.append(Component.text("Unit height is selected by right-clicking the ceiling, then the floor.", MUTED))
                    .append(Component.newline())
                    .append(button("Reset Height", "/claim height reset", ACCENT,
                            "Start the ceiling and floor selection again."))
                    .append(Component.space())
                    .append(button("Back", "/claim preview", MUTED, "Return to the claim preview."));
        } else {
            menu.append(button("Full Height", "/claim height full", ACCENT, "Use the full world height."))
                    .append(Component.space())
                    .append(button("Bottom Here", "/claim height bottom", ACCENT, "Use your current Y as the bottom."))
                    .append(Component.space())
                    .append(button("Top Here", "/claim height top", ACCENT, "Use your current Y as the top."))
                    .append(Component.space())
                    .append(button("Back", "/claim preview", MUTED, "Return to the claim preview."));
        }
        player.sendMessage(menu.build());
    }

    public static void sendApartmentHeightPrompt(Player player, ClaimSession session) {
        if (session.type() != ClaimType.UNIT || !session.closed()) {
            return;
        }
        String instruction = switch (session.apartmentHeightStep()) {
            case CEILING -> "Right-click the ceiling.";
            case FLOOR -> "Ceiling set. Right-click the floor.";
            case DONE -> "Height set: Y " + session.minY() + " to " + session.maxY() + ".";
        };
        player.sendActionBar(Component.text("Unit height | " + instruction, MUTED));

        Component line = prefix()
                .append(Component.text(instruction, TEXT));
        if (session.apartmentHeightStep() != ClaimSession.ApartmentHeightStep.CEILING) {
            line = line.append(Component.space())
                    .append(button("Reset Height", "/claim height reset", ACCENT,
                            "Start the ceiling and floor selection again."));
        }
        player.sendMessage(line);
    }

    public static void sendTerritorySetup(Player player, ClaimSession session) {
        String name = session.territoryName() == null || session.territoryName().isBlank()
                ? "Not set" : session.territoryName();
        String flag = session.territoryFlag() == null ? "Not set" : "Captured";
        player.sendActionBar(Component.text("Territory | " + name + " | Flag: " + flag, MUTED));

        Component menu = prefix()
                .append(Component.text("Territory setup", TEXT))
                .append(Component.newline())
                .append(Component.text("Name: " + name + " | Flag: " + flag, MUTED))
                .append(Component.newline())
                .append(suggestButton("Set Name", "/claim territory name ", ACCENT,
                        "Type the unique territory name after the command."))
                .append(Component.space())
                .append(button("Set Held Flag", "/claim territory flag", ACCENT,
                        "Hold the banner you want to use as the territory flag, then click."))
                .append(Component.space())
                .append(button("Back", "/claim preview", MUTED, "Return to the claim preview."));
        player.sendMessage(menu);
    }

    public static void sendExistingClaimSettings(Player player, Claim claim) {
        player.sendActionBar(Component.text("Claim settings | "
                + claim.type().name().toLowerCase().replace('_', ' ') + " | "
                + claim.id().toString().substring(0, 8), MUTED));

        TextComponent.Builder menu = Component.text()
                .append(prefix())
                .append(Component.text("Claim settings", TEXT));

        for (ClaimPermission permission : ClaimPermission.values()) {
            PermissionValue value = claim.permission(ClaimSubject.PUBLIC, permission);
            TextColor color = switch (value) {
                case ALLOW -> POSITIVE;
                case DENY -> NEGATIVE;
                case INHERIT -> MUTED;
            };
            String command = "/claim permission " + permission.name().toLowerCase() + " cycle";
            menu.append(Component.newline())
                    .append(Component.text(permission.displayName() + ": ", TEXT))
                    .append(button(prettyValue(value), command, color, "Click to cycle: inherit, allow, deny."));
        }

        player.sendMessage(menu.build());
    }

    private static String prettyValue(PermissionValue value) {
        String raw = value.name().toLowerCase();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static Component prefix() {
        return Component.text("[Server] ", PREFIX);
    }

    private static Component button(String label, String command, TextColor color, String hover) {
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, MUTED)));
    }

    private static Component suggestButton(String label, String command, TextColor color, String hover) {
        return Component.text("[" + label + "]", color)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(hover, MUTED)));
    }
}
