package com.herasgarden.gardencore.platform.economy;

import com.herasgarden.gardencore.api.economy.GardenEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class VaultGardenEconomy implements GardenEconomy {
    private final Economy economy;
    private final String symbol;

    public VaultGardenEconomy(Economy economy, String symbol) {
        this.economy = economy;
        this.symbol = symbol == null || symbol.isBlank() ? "⟡" : symbol;
    }

    @Override
    public long balance(UUID playerUuid) {
        return (long) Math.floor(economy.getBalance(player(playerUuid)));
    }

    @Override
    public boolean has(UUID playerUuid, long amount) {
        return amount >= 0 && economy.has(player(playerUuid), amount);
    }

    @Override
    public boolean withdraw(UUID playerUuid, long amount) {
        if (amount < 0 || !has(playerUuid, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player(playerUuid), amount).transactionSuccess();
    }

    @Override
    public boolean deposit(UUID playerUuid, long amount) {
        if (amount < 0) {
            return false;
        }
        return economy.depositPlayer(player(playerUuid), amount).transactionSuccess();
    }

    @Override
    public String symbol() {
        return symbol;
    }

    private OfflinePlayer player(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("Player UUID is required.");
        }
        return Bukkit.getOfflinePlayer(uuid);
    }
}
