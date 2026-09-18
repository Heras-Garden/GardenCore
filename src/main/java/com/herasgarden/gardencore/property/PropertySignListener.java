package com.herasgarden.gardencore.property;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.util.Messages;
import com.herasgarden.gardencore.worldguard.WorldGuardHook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.HashSet;
import java.util.Set;

public final class PropertySignListener implements Listener {
    private final GardenCore plugin;
    private final PropertyRegistry registry;
    private final WorldGuardHook worldGuard;
    private final Economy economy;
    private final Set<String> purchasesInProgress = new HashSet<>();

    public PropertySignListener(GardenCore plugin, PropertyRegistry registry, WorldGuardHook worldGuard, Economy economy) {
        this.plugin = plugin;
        this.registry = registry;
        this.worldGuard = worldGuard;
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("gardencore.property.admin")) {
            return;
        }

        String road = safe(event.getLine(0));
        String number = safe(event.getLine(1));
        if (road.isBlank() || number.isBlank()) {
            return;
        }

        Property property = registry.get(road, number);
        if (property == null) {
            return;
        }

        property.setSignLocation(event.getBlock().getLocation());
        event.setLine(0, property.road());
        event.setLine(1, property.number());
        event.setLine(2, property.ownerName() == null ? priceLine(property) : property.ownerName());
        event.setLine(3, "");

        if (plugin.getConfig().getBoolean("properties.auto-bind-mailbox-from-sign", true)) {
            Block mailbox = supportingMailbox(event.getBlock());
            if (mailbox != null) {
                property.setMailboxLocation(mailbox.getLocation());
            }
        }

        if (registry.saveQuietly()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> Messages.send(player, "Property sign linked to " + property.number() + " " + property.road() + "."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBoundSignBreak(BlockBreakEvent event) {
        Property property = registry.getBySign(event.getBlock().getLocation());
        if (property == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("gardencore.property.admin")) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "This property sign is managed by the server.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }

        Property property = registry.getBySign(sign.getLocation());
        if (property == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (!property.forSale()) {
            String owner = property.ownerName() == null ? "Not for sale" : property.ownerName();
            Messages.send(player, property.number() + " " + property.road() + " | " + owner + ".");
            return;
        }

        if (property.ownerUuid() != null) {
            Messages.send(player, "This property already has an owner.");
            return;
        }

        if (!worldGuard.isAvailable()) {
            Messages.send(player, "Property purchases are temporarily unavailable.");
            return;
        }

        String purchaseKey = property.key();
        if (!purchasesInProgress.add(purchaseKey)) {
            Messages.send(player, "Someone is already purchasing this property.");
            return;
        }

        try {
            long price = property.price();
            if (!economy.has(player, price)) {
                long missing = Math.max(1L, (long) Math.ceil(price - economy.getBalance(player)));
                Messages.send(player, "You need " + missing + " more ⟡ Obols.");
                return;
            }

            World world = Bukkit.getWorld(property.worldName());
            if (world == null) {
                Messages.send(player, "This property's world is unavailable.");
                return;
            }

            EconomyResponse withdrawal = economy.withdrawPlayer(player, price);
            if (!withdrawal.transactionSuccess()) {
                Messages.send(player, "The purchase could not be completed.");
                return;
            }

            if (!worldGuard.assignOwner(world, property.regionId(), player.getUniqueId())) {
                economy.depositPlayer(player, price);
                Messages.send(player, "The property could not be transferred. Your Obols were returned.");
                return;
            }

            property.setOwner(player.getUniqueId(), player.getName());
            property.setForSale(false);
            if (!registry.saveQuietly()) {
                property.clearOwner();
                property.setForSale(true);
                worldGuard.clearOwners(world, property.regionId());
                economy.depositPlayer(player, price);
                Messages.send(player, "The property could not be saved. Your Obols were returned.");
                plugin.getLogger().severe("Property persistence failed after purchase for " + property.key()
                        + ". The transaction was rolled back.");
                return;
            }

            sign.setLine(0, property.road());
            sign.setLine(1, property.number());
            sign.setLine(2, player.getName());
            sign.setLine(3, "");
            sign.update(true, false);

            Messages.send(player, "Property purchased for ⟡ " + price + ".");
        } finally {
            purchasesInProgress.remove(purchaseKey);
        }
    }

    private String priceLine(Property property) {
        String prefix = plugin.getConfig().getString("properties.sign-price-prefix", "⟡ ");
        return prefix + property.price();
    }

    private Block supportingMailbox(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        Block support;
        if (data instanceof WallSign wallSign) {
            support = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        } else {
            support = signBlock.getRelative(0, -1, 0);
        }

        Material type = support.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL ? support : null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
