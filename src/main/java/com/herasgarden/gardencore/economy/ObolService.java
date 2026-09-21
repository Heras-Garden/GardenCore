package com.herasgarden.gardencore.economy;

import com.herasgarden.gardencore.GardenCore;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ObolService {
    private final GardenCore plugin;
    private final Economy economy;
    private final NamespacedKey obolKey;

    public ObolService(GardenCore plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.obolKey = new NamespacedKey(plugin, "obol");
    }

    public double balance(Player player) {
        return economy.getBalance(player);
    }

    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public String formatAmount(double amount) {
        return Long.toString((long) Math.floor(amount));
    }

    public boolean adminAdd(OfflinePlayer player, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean adminRemove(OfflinePlayer player, int amount) {
        if (player == null || amount <= 0 || !economy.has(player, amount)) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdrawToPhysical(Player player, int amount) {
        return withdrawToPhysicalResult(player, amount).status() == WithdrawalStatus.FULL;
    }

    public WithdrawalResult withdrawToPhysicalResult(Player player, int amount) {
        if (amount <= 0 || !economy.has(player, amount) || !hasSpaceFor(player.getInventory(), amount)) {
            return new WithdrawalResult(WithdrawalStatus.FAILED, 0, 0, 0);
        }

        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            return new WithdrawalResult(WithdrawalStatus.FAILED, 0, 0, 0);
        }

        int delivered = 0;
        int compensated = 0;
        int unresolved = 0;
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createObol(stackSize));
            int notGiven = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            delivered += stackSize - notGiven;
            remaining -= stackSize;

            if (notGiven > 0) {
                EconomyResponse refund = economy.depositPlayer(player, notGiven);
                if (refund.transactionSuccess()) {
                    compensated += notGiven;
                } else {
                    unresolved += notGiven;
                    plugin.getLogger().severe("Physical Obol withdrawal left " + notGiven
                            + " Obols unresolved for " + player.getUniqueId() + ".");
                }
                break;
            }
        }

        if (delivered == amount && unresolved == 0) {
            return new WithdrawalResult(WithdrawalStatus.FULL, delivered, compensated, unresolved);
        }
        if (delivered > 0) {
            return new WithdrawalResult(WithdrawalStatus.PARTIAL, delivered, compensated, unresolved);
        }
        return new WithdrawalResult(WithdrawalStatus.FAILED, delivered, compensated, unresolved);
    }

    public boolean depositPhysical(Player player, int amount) {
        if (amount <= 0 || countOfficialObols(player.getInventory()) < amount) {
            return false;
        }

        if (!removeOfficialObols(player.getInventory(), amount)) {
            return false;
        }

        EconomyResponse response = economy.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            giveObols(player, amount);
            return false;
        }
        return true;
    }

    public ItemStack createObol(int amount) {
        ItemStack item = new ItemStack(Material.EMERALD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(plugin.getConfig().getString("obols.display-name", "Obol"));
        List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("obols.lore"));
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.getPersistentDataContainer().set(obolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Only server-created PDC metadata is authoritative physical-currency identity. */
    public boolean isOfficialObol(ItemStack item) {
        if (item == null || item.getType() != Material.EMERALD || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(obolKey, PersistentDataType.BYTE);
    }

    public int countOfficialObols(PlayerInventory inventory) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isOfficialObol(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean removeOfficialObols(PlayerInventory inventory, int amount) {
        if (countOfficialObols(inventory) < amount) {
            return false;
        }

        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isOfficialObol(item)) {
                continue;
            }

            int remove = Math.min(remaining, item.getAmount());
            int newAmount = item.getAmount() - remove;
            if (newAmount <= 0) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(newAmount);
            }
            remaining -= remove;
        }
        return remaining == 0;
    }

    private boolean hasSpaceFor(PlayerInventory inventory, int amount) {
        int capacity = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                capacity += 64;
            } else if (isOfficialObol(item)) {
                capacity += Math.max(0, item.getMaxStackSize() - item.getAmount());
            }
            if (capacity >= amount) {
                return true;
            }
        }
        return capacity >= amount;
    }

    public enum WithdrawalStatus {
        FULL,
        PARTIAL,
        FAILED
    }

    public record WithdrawalResult(WithdrawalStatus status, int delivered, int compensated, int unresolved) {
    }

    private void giveObols(Player player, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(64, remaining);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(createObol(stackSize));
            leftovers.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stackSize;
        }
    }
}
