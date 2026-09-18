package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.organization.Organization;
import com.herasgarden.gardencore.organization.OrganizationPermission;
import com.herasgarden.gardencore.organization.OrganizationService;
import com.herasgarden.gardencore.organization.OrganizationType;
import com.herasgarden.gardencore.territory.TerritoryService;
import com.herasgarden.gardencore.property.PropertyService;
import com.herasgarden.gardencore.property.SqlProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import com.herasgarden.gardencore.ui.ClaimChatUi;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class ClaimCommand {
    private final ClaimService claims;
    private final ClaimSessionManager sessions;
    private final OrganizationService organizations;
    private final TerritoryService territories;
    private final PropertyService properties;

    public ClaimCommand(ClaimService claims, ClaimSessionManager sessions, OrganizationService organizations,
                        TerritoryService territories, PropertyService properties) {
        this.claims = claims;
        this.sessions = sessions;
        this.organizations = organizations;
        this.territories = territories;
        this.properties = properties;
    }

    public boolean handle(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Claim commands must be used in-game.");
            return true;
        }
        if (args.length < 2) {
            help(player);
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "start" -> start(player, args);
            case "confirm" -> { sessions.confirm(player); yield true; }
            case "cancel" -> { sessions.cancel(player); yield true; }
            case "undo" -> undo(player);
            case "edit" -> edit(player);
            case "preview" -> { sessions.showPreview(player); yield true; }
            case "settings" -> settings(player);
            case "height" -> height(player, args);
            case "name" -> name(player, args);
            case "territory" -> territory(player, args);
            case "info" -> info(player);
            case "permission" -> permission(player, args);
            case "delete" -> delete(player, args);
            default -> { help(player); yield true; }
        };
    }

    private boolean start(Player player, String[] args) {
        if (!player.hasPermission("gardencore.claim")) {
            Messages.send(player, "You do not have permission to create claims.");
            return true;
        }
        if (args.length < 3) {
            Messages.send(player, "Use /claim start <type> [company|government <name>].");
            return true;
        }

        ClaimOwnerType ownerType = ClaimOwnerType.PLAYER;
        java.util.UUID ownerId = player.getUniqueId();
        String ownerLabel = player.getName();

        ClaimType type;
        String organizationKeyword = null;
        int organizationNameStart = -1;

        if (args[2].equalsIgnoreCase("company") || args[2].equalsIgnoreCase("government")) {
            if (args.length < 5) {
                Messages.send(player, "Use /claim start <type> " + args[2].toLowerCase(Locale.ROOT) + " <name>.");
                return true;
            }
            organizationKeyword = args[2];
            type = parseType(args[3]);
            organizationNameStart = 4;
        } else {
            type = parseType(args[2]);
            if (args.length >= 4
                    && (args[3].equalsIgnoreCase("company") || args[3].equalsIgnoreCase("government"))) {
                if (args.length < 5) {
                    Messages.send(player, "Use /claim start " + args[2].toLowerCase(Locale.ROOT) + " "
                            + args[3].toLowerCase(Locale.ROOT) + " <name>.");
                    return true;
                }
                organizationKeyword = args[3];
                organizationNameStart = 4;
            }
        }

        if (type == null) {
            Messages.send(player, "Unknown claim type.");
            return true;
        }

        if (organizationKeyword != null) {
            OrganizationType organizationType = organizationKeyword.equalsIgnoreCase("company")
                    ? OrganizationType.COMPANY : OrganizationType.GOVERNMENT;
            String organizationName = join(args, organizationNameStart);
            Organization organization = organizations.find(organizationType, organizationName);
            if (organization == null) {
                Messages.send(player, "That " + organizationType.name().toLowerCase(Locale.ROOT) + " does not exist.");
                return true;
            }
            if (!organizations.has(player, organization, OrganizationPermission.CLAIM_CREATE)
                    && !player.hasPermission("gardencore.claim.admin")) {
                Messages.send(player, "Your position does not allow you to create claims for " + organization.name() + ".");
                return true;
            }
            ownerType = organizationType == OrganizationType.COMPANY
                    ? ClaimOwnerType.COMPANY : ClaimOwnerType.GOVERNMENT;
            ownerId = organization.id();
            ownerLabel = organization.name();
        }

        if (type == ClaimType.TERRITORY && !player.hasPermission("gardencore.claim.territory")) {
            Messages.send(player, "You do not have permission to create a territory claim yet.");
            return true;
        }

        ClaimSession session = sessions.start(player, type, ownerType, ownerId);
        Messages.send(player, "Claim started: " + pretty(type) + " for " + ownerLabel + ". Right-click the first corner.");
        if (session.shape() == ClaimShape.POLYGON) {
            Messages.send(player, "Select at least three points, then click the first point again to close the boundary.");
        } else {
            Messages.send(player, "Select two opposite corners, then click the first point again to close the boundary.");
        }
        if (type == ClaimType.APARTMENT) {
            Messages.send(player, "After the apartment boundary is closed, right-click the ceiling, then right-click the floor.");
        }
        ClaimChatUi.sendSelectionControls(player, session, null);
        if (type == ClaimType.TERRITORY) {
            Messages.send(player, "A territory also needs a unique name and a banner flag. Hold the banner you want to use as the flag.");
            ClaimChatUi.sendTerritorySetup(player, session);
        }
        return true;
    }

    private boolean name(Player player, String[] args) {
        ClaimSession session = sessions.get(player);
        if (session == null) {
            Messages.send(player, "Start a city or district claim first.");
            return true;
        }
        if (session.type() != ClaimType.CITY && session.type() != ClaimType.DISTRICT) {
            Messages.send(player, "/claim name is only used while creating a city or district.");
            return true;
        }
        if (args.length < 3) {
            Messages.send(player, "Use /claim name <name>.");
            return true;
        }

        String name = join(args, 2).trim();
        if (name.length() < 3 || name.length() > 48) {
            Messages.send(player, "City and district names must be between 3 and 48 characters.");
            return true;
        }

        session.setClaimName(name);
        Messages.send(player, pretty(session.type()) + " name set to " + name + ".");
        sessions.showPreview(player);
        return true;
    }

    private boolean territory(Player player, String[] args) {
        ClaimSession session = sessions.get(player);
        if (session == null || session.type() != ClaimType.TERRITORY) {
            Messages.send(player, "Start a territory claim first with /claim start territory.");
            return true;
        }
        if (args.length < 3 || args[2].equalsIgnoreCase("status")) {
            ClaimChatUi.sendTerritorySetup(player, session);
            return true;
        }

        if (args[2].equalsIgnoreCase("name")) {
            if (args.length < 4) {
                Messages.send(player, "Use /claim territory name <territory name>.");
                return true;
            }
            String name = join(args, 3).trim();
            if (name.length() < 3 || name.length() > 48) {
                Messages.send(player, "Territory names must be between 3 and 48 characters.");
                return true;
            }
            if (!territories.isNameAvailable(name)) {
                Messages.send(player, "That territory name is already in use.");
                return true;
            }
            session.setTerritoryName(name);
            Messages.send(player, "Territory name set to " + name + ".");
            ClaimChatUi.sendTerritorySetup(player, session);
            return true;
        }

        if (args[2].equalsIgnoreCase("flag")) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!territories.isBanner(held)) {
                Messages.send(player, "Hold the banner you want to use as the territory flag, then click Set Held Flag again.");
                return true;
            }
            session.setTerritoryFlag(held);
            Messages.send(player, "Territory flag captured from the banner in your hand.");
            ClaimChatUi.sendTerritorySetup(player, session);
            return true;
        }

        ClaimChatUi.sendTerritorySetup(player, session);
        return true;
    }

    private boolean undo(Player player) {
        ClaimSession session = sessions.get(player);
        if (session == null) {
            Messages.send(player, "You are not creating a claim.");
            return true;
        }
        if (!session.undo()) {
            Messages.send(player, "There is nothing to undo.");
            return true;
        }
        Messages.send(player, session.closed() ? "Claim reopened." : "Last claim step undone.");
        sessions.showPreview(player);
        return true;
    }

    private boolean edit(Player player) {
        ClaimSession session = sessions.get(player);
        if (session == null) {
            Messages.send(player, "You are not creating a claim.");
            return true;
        }
        session.reopen();
        Messages.send(player, "Claim reopened. Adjust the boundary, then click the first point again.");
        ClaimChatUi.sendSelectionControls(player, session, null);
        return true;
    }

    private boolean settings(Player player) {
        ClaimSession session = sessions.get(player);
        if (session != null) {
            ClaimChatUi.sendSettings(player, session);
            return true;
        }

        Claim claim = claims.findAt(player.getLocation());
        if (claim == null) {
            Messages.send(player, "Stand inside a claim or start creating one first.");
            return true;
        }
        if (!claims.canManage(player, claim)) {
            Messages.send(player, "You do not manage this claim.");
            return true;
        }
        ClaimChatUi.sendExistingClaimSettings(player, claim);
        return true;
    }

    private boolean height(Player player, String[] args) {
        ClaimSession session = sessions.get(player);
        if (session == null) {
            Messages.send(player, "You are not creating a claim.");
            return true;
        }
        if (args.length < 3) {
            ClaimChatUi.sendSettings(player, session);
            return true;
        }

        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "reset" -> {
                if (session.type() != ClaimType.APARTMENT) {
                    ClaimChatUi.sendSettings(player, session);
                    return true;
                }
                session.resetApartmentHeight();
                Messages.send(player, "Apartment height reset. Right-click the ceiling, then right-click the floor.");
                ClaimChatUi.sendApartmentHeightPrompt(player, session);
                return true;
            }
            case "full" -> {
                if (session.type() == ClaimType.APARTMENT) {
                    Messages.send(player, "Apartment height is selected by right-clicking the ceiling, then the floor.");
                    ClaimChatUi.sendApartmentHeightPrompt(player, session);
                    return true;
                }
                session.useFullHeight(player.getWorld());
            }
            case "bottom", "top" -> {
                if (session.type() == ClaimType.APARTMENT) {
                    Messages.send(player, "Apartment height is selected by right-clicking the ceiling, then the floor.");
                    ClaimChatUi.sendApartmentHeightPrompt(player, session);
                    return true;
                }
                if (args[2].equalsIgnoreCase("bottom")) {
                    session.setBottom(player.getLocation().getBlockY());
                } else {
                    session.setTop(player.getLocation().getBlockY());
                }
            }
            default -> {
                ClaimChatUi.sendSettings(player, session);
                return true;
            }
        }
        Messages.send(player, session.fullHeight() ? "Height set to the full world."
                : "Height set to Y " + session.minY() + " through Y " + session.maxY() + ".");
        ClaimChatUi.sendSettings(player, session);
        return true;
    }

    private boolean info(Player player) {
        Claim claim = claims.findAt(player.getLocation());
        if (claim == null) {
            Messages.send(player, "There is no GardenCore claim here.");
            return true;
        }
        ClaimGeometry geometry = claim.geometry();
        String size = geometry.width() + " x " + geometry.length();
        if (!geometry.fullHeight()) {
            size += " x " + geometry.height();
        }
        String name = claim.name() == null || claim.name().isBlank() ? "" : " | " + claim.name();
        Messages.send(player, "Claim " + claim.id().toString().substring(0, 8)
                + " | " + pretty(claim.type()) + name
                + " | " + size
                + " | " + geometry.blockAreaEstimate() + " blocks²");
        return true;
    }

    private boolean permission(Player player, String[] args) {
        if (args.length < 4) {
            Messages.send(player, "Use the clickable claim settings with /claim settings.");
            return true;
        }
        Claim claim = claims.findAt(player.getLocation());
        if (claim == null) {
            Messages.send(player, "Stand inside the claim you want to manage.");
            return true;
        }
        if (!claims.canManage(player, claim)) {
            Messages.send(player, "You do not manage this claim.");
            return true;
        }

        ClaimPermission permission;
        try {
            permission = ClaimPermission.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Messages.send(player, "Unknown claim setting.");
            return true;
        }
        if (!args[3].equalsIgnoreCase("cycle")) {
            Messages.send(player, "Use the clickable claim settings with /claim settings.");
            return true;
        }

        try {
            claims.cyclePublicPermission(claim, permission);
            ClaimChatUi.sendExistingClaimSettings(player, claim);
        } catch (SQLException exception) {
            Messages.send(player, "That setting could not be saved.");
        }
        return true;
    }

    private boolean delete(Player player, String[] args) {
        if (args.length >= 3 && args[2].equalsIgnoreCase("cancel")) {
            Messages.send(player, "Claim deletion cancelled.");
            return true;
        }

        if (args.length >= 4 && args[2].equalsIgnoreCase("confirm")) {
            java.util.UUID id;
            try {
                id = java.util.UUID.fromString(args[3]);
            } catch (IllegalArgumentException exception) {
                Messages.send(player, "That claim deletion link is no longer valid.");
                return true;
            }
            Claim claim = claims.get(id);
            if (claim == null) {
                Messages.send(player, "That claim no longer exists.");
                return true;
            }
            if (!claims.canManage(player, claim)) {
                Messages.send(player, "You do not manage this claim.");
                return true;
            }
            if (claims.hasChildren(claim)) {
                Messages.send(player, "This claim contains child claims. Delete or transfer the child claims first.");
                return true;
            }

            try {
                String label = claimLabel(claim);
                SqlProperty property = properties.getByClaim(claim.id());
                if (property != null) {
                    properties.deleteProperty(property);
                } else {
                    if (claim.type() == ClaimType.TERRITORY) {
                        territories.delete(claim);
                    }
                    claims.delete(claim);
                }
                Messages.send(player, "Deleted " + label + ".");
            } catch (IllegalArgumentException exception) {
                Messages.send(player, exception.getMessage());
            } catch (SQLException exception) {
                Messages.send(player, "The claim could not be deleted safely.");
            }
            return true;
        }

        Claim claim;
        if (args.length >= 3) {
            claim = findByIdPrefix(args[2]);
            if (claim == null) {
                Messages.send(player, "No claim matches that ID.");
                return true;
            }
        } else {
            claim = claims.findAt(player.getLocation());
            if (claim == null) {
                Messages.send(player, "Stand inside the claim you want to delete, or use /claim delete <claim-id>.");
                return true;
            }
        }

        if (!claims.canManage(player, claim)) {
            Messages.send(player, "You do not manage this claim.");
            return true;
        }
        if (claims.hasChildren(claim)) {
            Messages.send(player, "This " + pretty(claim.type())
                    + " contains child claims. Delete or transfer the child claims first.");
            return true;
        }

        Component prompt = Messages.prefix()
                .append(Component.text("Delete " + claimLabel(claim) + "? This cannot be undone.", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("[Delete Claim]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/claim delete confirm " + claim.id()))
                        .hoverEvent(HoverEvent.showText(Component.text("Permanently delete this claim."))))
                .append(Component.space())
                .append(Component.text("[Cancel]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.runCommand("/claim delete cancel")));
        player.sendMessage(prompt);
        return true;
    }

    private Claim findByIdPrefix(String token) {
        String prefix = token.toLowerCase(Locale.ROOT);
        List<Claim> matches = claims.all().stream()
                .filter(claim -> claim.id().toString().toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private String claimLabel(Claim claim) {
        String name = claim.name() == null || claim.name().isBlank() ? "" : " " + claim.name();
        return pretty(claim.type()) + name + " (" + claim.id().toString().substring(0, 8) + ")";
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return match(args[1], List.of("start", "confirm", "cancel", "undo", "edit", "preview", "settings", "height", "name", "territory", "info", "delete"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("start")) {
            return match(args[2], claimTypeNames());
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("start") && parseType(args[2]) != null) {
            return match(args[3], List.of("company", "government"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("height")) {
            return match(args[2], List.of("full", "bottom", "top"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("territory")) {
            return match(args[2], List.of("name", "flag", "status"));
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("delete")) {
            List<String> ids = new java.util.ArrayList<>();
            ids.add("cancel");
            claims.all().forEach(claim -> ids.add(claim.id().toString().substring(0, 8)));
            return match(args[2], ids);
        }
        return List.of();
    }

    private List<String> claimTypeNames() {
        return List.of("property", "house", "farm", "shop", "building", "apartment", "hotel",
                "city", "district", "venue", "theater", "harbor", "rail_station", "packing_station",
                "mule_station", "civic", "protected", "territory");
    }

    private ClaimType parseType(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "property", "plot", "home", "house" -> ClaimType.PROPERTY;
            case "shop" -> ClaimType.SHOP;
            case "farm" -> ClaimType.FARM;
            case "building" -> ClaimType.BUILDING;
            case "apartment", "apt" -> ClaimType.APARTMENT;
            case "hotel", "hotel_room", "room" -> ClaimType.HOTEL_ROOM;
            case "territory" -> ClaimType.TERRITORY;
            case "city" -> ClaimType.CITY;
            case "district" -> ClaimType.DISTRICT;
            case "government", "government_land", "civic" -> ClaimType.GOVERNMENT;
            case "company", "company_land" -> ClaimType.COMPANY;
            case "venue", "theater", "theatre", "arena" -> ClaimType.VENUE;
            case "harbor", "harbour" -> ClaimType.HARBOR;
            case "rail", "rail_station", "station" -> ClaimType.RAIL_STATION;
            case "packing", "packing_station" -> ClaimType.PACKING_STATION;
            case "mule", "mule_station" -> ClaimType.MULE_STATION;
            case "protected", "garden" -> ClaimType.PROTECTED;
            default -> null;
        };
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private String pretty(ClaimType type) {
        String value = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private List<String> match(String prefix, List<String> options) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).toList();
    }

    private void help(Player player) {
        Messages.send(player, "Use /claim start <type>, /claim info, or /claim settings. City/district creation also uses /claim name <name>. /garden claim also works.");
    }
}
