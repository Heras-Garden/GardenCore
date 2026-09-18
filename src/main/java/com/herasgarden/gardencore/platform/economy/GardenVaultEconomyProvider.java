package com.herasgarden.gardencore.platform.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Vault provider backed by GardenCore's own Obol ledger. This lets the server
 * remove EssentialsX economy while keeping third-party Vault integrations.
 */
@SuppressWarnings("deprecation")
public final class GardenVaultEconomyProvider implements Economy {
    private final GardenBalanceService balances;
    private final String singular;
    private final String plural;

    public GardenVaultEconomyProvider(GardenBalanceService balances, String singular, String plural) {
        this.balances = balances;
        this.singular = singular == null || singular.isBlank() ? "Obol" : singular;
        this.plural = plural == null || plural.isBlank() ? this.singular + "s" : plural;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "GardenCore"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 0; }
    @Override public String format(double amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format((long) Math.floor(amount)) + " " +
                (Math.floor(amount) == 1 ? singular : plural);
    }
    @Override public String currencyNamePlural() { return plural; }
    @Override public String currencyNameSingular() { return singular; }

    @Override public boolean hasAccount(String playerName) { return hasAccount(player(playerName)); }
    @Override public boolean hasAccount(OfflinePlayer player) { return player != null; }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

    @Override public double getBalance(String playerName) { return getBalance(player(playerName)); }
    @Override public double getBalance(OfflinePlayer player) {
        if (player == null) return 0D;
        try {
            return balances.balance(player.getUniqueId());
        } catch (SQLException exception) {
            return 0D;
        }
    }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override public boolean has(String playerName, double amount) { return has(player(playerName), amount); }
    @Override public boolean has(OfflinePlayer player, double amount) {
        if (player == null || amount < 0) return false;
        try {
            return balances.has(player.getUniqueId(), whole(amount));
        } catch (SQLException exception) {
            return false;
        }
    }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(player(playerName), amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null || amount < 0) return failure(amount, player, "Invalid withdrawal.");
        long whole = whole(amount);
        try {
            boolean ok = balances.withdraw(player.getUniqueId(), whole);
            return ok ? success(whole, player) : failure(whole, player, "Insufficient Obols.");
        } catch (SQLException exception) {
            return failure(whole, player, "Garden economy storage error.");
        }
    }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(player(playerName), amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null || amount < 0) return failure(amount, player, "Invalid deposit.");
        long whole = whole(amount);
        try {
            boolean ok = balances.deposit(player.getUniqueId(), whole);
            return ok ? success(whole, player) : failure(whole, player, "Garden economy storage error.");
        } catch (SQLException exception) {
            return failure(whole, player, "Garden economy storage error.");
        }
    }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override public boolean createPlayerAccount(String playerName) { return createPlayerAccount(player(playerName)); }
    @Override public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) return false;
        try {
            balances.balance(player.getUniqueId());
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return createPlayerAccount(playerName); }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }

    @Override public EconomyResponse createBank(String name, String player) { return notImplemented(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse deleteBank(String name) { return notImplemented(); }
    @Override public EconomyResponse bankBalance(String name) { return notImplemented(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return notImplemented(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return notImplemented(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return notImplemented(); }
    @Override public List<String> getBanks() { return List.of(); }

    private EconomyResponse success(double amount, OfflinePlayer player) {
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(double amount, OfflinePlayer player, String detail) {
        return new EconomyResponse(amount, player == null ? 0D : getBalance(player),
                EconomyResponse.ResponseType.FAILURE, detail);
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0D, 0D, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "GardenCore does not use Vault banks.");
    }

    private long whole(double amount) {
        if (!Double.isFinite(amount)) return -1L;
        return (long) Math.floor(amount);
    }

    private OfflinePlayer player(String name) {
        if (name == null || name.isBlank()) return null;
        return Bukkit.getOfflinePlayer(name);
    }
}
