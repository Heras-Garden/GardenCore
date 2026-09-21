package com.herasgarden.gardencore.economy;

import com.herasgarden.gardencore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class ObolCommand implements CommandExecutor, TabCompleter {
    private final ObolService obols;

    public ObolCommand(ObolService obols) {
        this.obols = obols;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return admin(sender, args);
        }

        if (!(sender instanceof Player player)) {
            Messages.send(sender, "Player Obol commands must be used in-game. Use /obol admin for administration.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            Messages.send(player, "Balance: ⟡ " + obols.formatAmount(obols.balance(player)));
            return true;
        }

        if (args[0].equalsIgnoreCase("withdraw")) {
            Integer amount = parseAmount(args, 1);
            if (amount == null) {
                Messages.send(player, "Use /obol withdraw <amount>.");
                return true;
            }
            double balance = obols.balance(player);
            if (balance < amount) {
                long missing = Math.max(1L, (long) Math.ceil(amount - balance));
                Messages.send(player, "You need " + missing + " more ⟡ Obols.");
                return true;
            }
            ObolService.WithdrawalResult result = obols.withdrawToPhysicalResult(player, amount);
            if (result.status() == ObolService.WithdrawalStatus.FULL) {
                Messages.send(player, "Withdrew ⟡ " + amount + ".");
                return true;
            }
            if (result.status() == ObolService.WithdrawalStatus.PARTIAL) {
                String unresolved = result.unresolved() > 0
                        ? " " + result.unresolved() + " Obols require staff reconciliation."
                        : "";
                Messages.send(player, "Partial withdrawal: " + result.delivered()
                        + " physical Obols were delivered and " + result.compensated()
                        + " were returned to your balance." + unresolved);
                return true;
            }
            Messages.send(player, "The withdrawal could not be completed. Check your inventory space.");
            return true;
        }

        if (args[0].equalsIgnoreCase("deposit")) {
            int available = obols.countOfficialObols(player.getInventory());
            if (available <= 0) {
                Messages.send(player, "You do not have any physical Obols to deposit.");
                return true;
            }

            int amount;
            if (args.length < 2 || args[1].equalsIgnoreCase("all")) {
                amount = available;
            } else {
                Integer parsed = parseAmount(args, 1);
                if (parsed == null) {
                    Messages.send(player, "Use /obol deposit <amount|all>.");
                    return true;
                }
                amount = parsed;
            }

            if (available < amount) {
                Messages.send(player, "You only have " + available + " physical Obols.");
                return true;
            }

            if (!obols.depositPhysical(player, amount)) {
                Messages.send(player, "The deposit could not be completed.");
                return true;
            }
            Messages.send(player, "Deposited ⟡ " + amount + ".");
            return true;
        }

        Messages.send(player, "Use /obol balance, /obol withdraw <amount>, /obol deposit <amount|all>, or /obol admin.");
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("gardencore.obol.admin")) {
            Messages.send(sender, "You do not have permission to manage another player's Obols.");
            return true;
        }
        if (args.length < 4 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            Messages.send(sender, "Use /obol admin <add|remove> <player> <amount>.");
            return true;
        }

        Integer amount = parseAmount(args, 3);
        if (amount == null) {
            Messages.send(sender, "The amount must be a positive whole number.");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            Messages.send(sender, "That player has not joined this server before.");
            return true;
        }

        if (args[1].equalsIgnoreCase("add")) {
            if (!obols.adminAdd(target, amount)) {
                Messages.send(sender, "The Obols could not be added.");
                return true;
            }
            Messages.send(sender, "Added ⟡ " + amount + " to " + displayName(target) + ". New balance: ⟡ "
                    + obols.formatAmount(obols.balance(target)) + ".");
            if (target.getPlayer() != null) {
                Messages.send(target.getPlayer(), "An administrator added ⟡ " + amount + " to your balance.");
            }
            return true;
        }

        double current = obols.balance(target);
        if (current < amount) {
            Messages.send(sender, displayName(target) + " only has ⟡ " + obols.formatAmount(current) + ".");
            return true;
        }
        if (!obols.adminRemove(target, amount)) {
            Messages.send(sender, "The Obols could not be removed.");
            return true;
        }
        Messages.send(sender, "Removed ⟡ " + amount + " from " + displayName(target) + ". New balance: ⟡ "
                + obols.formatAmount(obols.balance(target)) + ".");
        if (target.getPlayer() != null) {
            Messages.send(target.getPlayer(), "An administrator removed ⟡ " + amount + " from your balance.");
        }
        return true;
    }

    private String displayName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString().substring(0, 8) : player.getName();
    }

    private Integer parseAmount(String[] args, int index) {
        if (args.length <= index) {
            return null;
        }
        try {
            int amount = Integer.parseInt(args[index]);
            return amount > 0 ? amount : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return match(args[0], sender.hasPermission("gardencore.obol.admin")
                    ? List.of("balance", "withdraw", "deposit", "admin")
                    : List.of("balance", "withdraw", "deposit"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("deposit")) {
            return match(args[1], List.of("all"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("gardencore.obol.admin")) {
            return match(args[1], List.of("add", "remove"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("gardencore.obol.admin")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.startsWith(lower)).toList();
    }
}
