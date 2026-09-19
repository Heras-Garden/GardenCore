package com.herasgarden.gardencore.social;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.api.economy.GardenEconomy;
import com.herasgarden.gardencore.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MarriageCommand implements CommandExecutor, TabCompleter {
    private final GardenCore plugin;
    private final MarriageService marriages;
    private final Map<UUID, Proposal> proposalsByTarget = new HashMap<>();
    private final Map<UUID, UUID> targetByProposer = new HashMap<>();

    public MarriageCommand(GardenCore plugin, MarriageService marriages) {
        this.plugin = plugin;
        this.marriages = marriages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Marriage commands must be used in-game.");
            return true;
        }

        cleanupExpired();

        if (args.length == 0) {
            info(player);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "cancel" -> cancel(player);
            case "info" -> { info(player); yield true; }
            case "divorce" -> divorce(player, args);
            case "help" -> { help(player); yield true; }
            case "propose" -> {
                if (args.length < 2) {
                    Messages.send(player, "Use /marry <player>.");
                    yield true;
                }
                yield propose(player, args[1]);
            }
            default -> propose(player, args[0]);
        };
    }

    private boolean propose(Player proposer, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            Messages.send(proposer, "That player must be online to receive a marriage proposal.");
            return true;
        }
        if (target.getUniqueId().equals(proposer.getUniqueId())) {
            Messages.send(proposer, "You cannot marry yourself.");
            return true;
        }
        if (marriages.married(proposer.getUniqueId())) {
            Messages.send(proposer, "You are already married.");
            return true;
        }
        if (marriages.married(target.getUniqueId())) {
            Messages.send(proposer, target.getName() + " is already married.");
            return true;
        }
        if (targetByProposer.containsKey(proposer.getUniqueId())) {
            Messages.send(proposer, "You already have an unanswered marriage proposal.");
            return true;
        }

        long expiresAt = System.currentTimeMillis()
                + Math.max(30L, plugin.getConfig().getLong("marriage.proposal-expire-seconds", 120L)) * 1000L;
        Proposal proposal = new Proposal(proposer.getUniqueId(), target.getUniqueId(), expiresAt);
        proposalsByTarget.put(target.getUniqueId(), proposal);
        targetByProposer.put(proposer.getUniqueId(), target.getUniqueId());

        long each = marriages.costPerPlayer();
        Messages.send(proposer, "Marriage proposal sent to " + target.getName() + ". If accepted, each of you will pay "
                + marriages.economy().symbol() + " " + each + ".");

        Component prompt = Messages.prefix()
                .append(Component.text(proposer.getName() + " wants to marry you. ", NamedTextColor.WHITE))
                .append(Component.text("Each player pays " + marriages.economy().symbol() + " " + each + ".",
                        NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("[Accept]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/marry accept"))
                        .hoverEvent(HoverEvent.showText(Component.text("Accept the marriage proposal."))))
                .append(Component.space())
                .append(Component.text("[Deny]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/marry deny"))
                        .hoverEvent(HoverEvent.showText(Component.text("Deny the marriage proposal."))));
        target.sendMessage(prompt);
        return true;
    }

    private boolean accept(Player target) {
        Proposal proposal = proposalsByTarget.get(target.getUniqueId());
        if (proposal == null || proposal.expired()) {
            removeProposal(proposal);
            Messages.send(target, "You do not have an active marriage proposal.");
            return true;
        }

        Player proposer = Bukkit.getPlayer(proposal.proposer());
        if (proposer == null || !proposer.isOnline()) {
            removeProposal(proposal);
            Messages.send(target, "That marriage proposal expired because the other player is offline.");
            return true;
        }
        if (marriages.married(proposer.getUniqueId()) || marriages.married(target.getUniqueId())) {
            removeProposal(proposal);
            Messages.send(target, "That marriage proposal is no longer valid.");
            return true;
        }

        long each = marriages.costPerPlayer();
        GardenEconomy economy = marriages.economy();
        if (!economy.has(proposer.getUniqueId(), each)) {
            Messages.send(target, proposer.getName() + " does not have enough Obols for the marriage.");
            Messages.send(proposer, "You need " + economy.symbol() + " " + each + " for the marriage.");
            return true;
        }
        if (!economy.has(target.getUniqueId(), each)) {
            Messages.send(target, "You need " + economy.symbol() + " " + each + " for the marriage.");
            Messages.send(proposer, target.getName() + " does not have enough Obols for the marriage.");
            return true;
        }

        if (!economy.withdraw(proposer.getUniqueId(), each)) {
            Messages.send(target, "The marriage payment could not be completed.");
            return true;
        }
        if (!economy.withdraw(target.getUniqueId(), each)) {
            economy.deposit(proposer.getUniqueId(), each);
            Messages.send(target, "The marriage payment could not be completed.");
            return true;
        }

        try {
            marriages.marry(proposer.getUniqueId(), target.getUniqueId());
        } catch (SQLException | IllegalArgumentException exception) {
            economy.deposit(proposer.getUniqueId(), each);
            economy.deposit(target.getUniqueId(), each);
            Messages.send(target, "The marriage could not be saved. Both payments were returned.");
            Messages.send(proposer, "The marriage could not be saved. Both payments were returned.");
            return true;
        }

        removeProposal(proposal);
        Messages.send(proposer, "You and " + target.getName() + " are now married.");
        Messages.send(target, "You and " + proposer.getName() + " are now married.");
        return true;
    }

    private boolean deny(Player target) {
        Proposal proposal = proposalsByTarget.remove(target.getUniqueId());
        if (proposal == null) {
            Messages.send(target, "You do not have an active marriage proposal.");
            return true;
        }
        targetByProposer.remove(proposal.proposer());
        Player proposer = Bukkit.getPlayer(proposal.proposer());
        if (proposer != null) Messages.send(proposer, target.getName() + " denied your marriage proposal.");
        Messages.send(target, "Marriage proposal denied.");
        return true;
    }

    private boolean cancel(Player proposer) {
        UUID targetId = targetByProposer.remove(proposer.getUniqueId());
        if (targetId == null) {
            Messages.send(proposer, "You do not have an active marriage proposal.");
            return true;
        }
        Proposal removed = proposalsByTarget.remove(targetId);
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) Messages.send(target, proposer.getName() + " cancelled the marriage proposal.");
        Messages.send(proposer, "Marriage proposal cancelled.");
        return true;
    }

    private boolean divorce(Player player, String[] args) {
        MarriageService.MarriageRecord record = marriages.marriage(player.getUniqueId()).orElse(null);
        if (record == null) {
            Messages.send(player, "You are not married.");
            return true;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            OfflinePlayer partner = Bukkit.getOfflinePlayer(record.partnerOf(player.getUniqueId()));
            String name = partner.getName() == null ? "your partner" : partner.getName();
            Component prompt = Messages.prefix()
                    .append(Component.text("Divorce " + name + "? This removes shared claim, home, and horse access.",
                            NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("[Confirm Divorce]", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/marry divorce confirm")))
                    .append(Component.space())
                    .append(Component.text("[Cancel]", NamedTextColor.GRAY)
                            .clickEvent(ClickEvent.runCommand("/marry info")));
            player.sendMessage(prompt);
            return true;
        }

        UUID partnerId = record.partnerOf(player.getUniqueId());
        try {
            marriages.divorce(player.getUniqueId());
        } catch (SQLException exception) {
            Messages.send(player, "The divorce could not be saved right now.");
            return true;
        }

        OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
        String partnerName = partner.getName() == null ? "your partner" : partner.getName();
        Messages.send(player, "You are no longer married to " + partnerName + ".");
        Player onlinePartner = Bukkit.getPlayer(partnerId);
        if (onlinePartner != null) {
            Messages.send(onlinePartner, player.getName() + " divorced you. Shared claim, home, and horse access has ended.");
        }
        return true;
    }

    private void info(Player player) {
        MarriageService.MarriageRecord record = marriages.marriage(player.getUniqueId()).orElse(null);
        if (record == null) {
            Messages.send(player, "You are not married. Use /marry <player> to propose. Marriage costs "
                    + marriages.economy().symbol() + " " + marriages.totalCost() + " total.");
            return;
        }

        OfflinePlayer partner = Bukkit.getOfflinePlayer(record.partnerOf(player.getUniqueId()));
        String partnerName = partner.getName() == null ? record.partnerOf(player.getUniqueId()).toString() : partner.getName();
        String date = DateTimeFormatter.ofPattern("MMM d, yyyy")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(record.marriedAt()));
        Messages.send(player, "Married to " + partnerName + " since " + date + ".");
    }

    private void help(Player player) {
        Messages.send(player, "/marry <player> - propose marriage");
        Messages.send(player, "/marry accept | deny | cancel");
        Messages.send(player, "/marry info");
        Messages.send(player, "/marry divorce");
    }

    private void cleanupExpired() {
        List<Proposal> expired = proposalsByTarget.values().stream().filter(Proposal::expired).toList();
        for (Proposal proposal : expired) removeProposal(proposal);
    }

    private void removeProposal(Proposal proposal) {
        if (proposal == null) return;
        proposalsByTarget.remove(proposal.target());
        targetByProposer.remove(proposal.proposer());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> values = new ArrayList<>(List.of("accept", "deny", "cancel", "info", "divorce", "help"));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.getUniqueId().equals(player.getUniqueId())) values.add(online.getName());
            }
            return values.stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("divorce")) {
            return "confirm".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("confirm") : List.of();
        }
        return List.of();
    }

    private record Proposal(UUID proposer, UUID target, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
