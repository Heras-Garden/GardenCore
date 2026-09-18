package com.herasgarden.gardencore.organization;

import com.herasgarden.gardencore.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class CompanyCommand {
    private final OrganizationService organizations;

    public CompanyCommand(OrganizationService organizations) {
        this.organizations = organizations;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "info" -> info(sender, args);
            case "list" -> list(sender);
            default -> { help(sender); yield true; }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Company creation must be done in-game.");
            return true;
        }
        if (!player.hasPermission("gardencore.company.create")) {
            Messages.send(player, "You do not have permission to create a company.");
            return true;
        }
        if (args.length < 3) {
            Messages.send(player, "Use /company create <name>.");
            return true;
        }
        String name = join(args, 2);
        try {
            Organization company = organizations.createCompany(player, name);
            Messages.send(player, "Company created: " + company.name() + ".");
            Messages.send(player, "You can claim for it with /claim start <type> company " + company.name() + ".");
        } catch (IllegalArgumentException exception) {
            Messages.send(player, exception.getMessage());
        } catch (SQLException exception) {
            Messages.send(player, "The company could not be created.");
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 3) {
            Messages.send(sender, "Use /company info <name>.");
            return true;
        }
        Organization company = organizations.find(OrganizationType.COMPANY, join(args, 2));
        if (company == null) {
            Messages.send(sender, "That company does not exist.");
            return true;
        }
        Messages.send(sender, company.name() + " | Treasury: ⟡ " + company.treasury()
                + " | Members: " + company.memberRoles().size() + ".");
        return true;
    }

    private boolean list(CommandSender sender) {
        List<Organization> companies = organizations.all().stream()
                .filter(org -> org.type() == OrganizationType.COMPANY)
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        Messages.send(sender, "Companies: " + companies.size() + ".");
        for (Organization company : companies) {
            sender.sendMessage("- " + company.name());
        }
        return true;
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("create", "info", "list").stream().filter(v -> v.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void help(CommandSender sender) {
        Messages.send(sender, "Use /company create, /company info, or /company list. /garden company also works.");
    }
}
