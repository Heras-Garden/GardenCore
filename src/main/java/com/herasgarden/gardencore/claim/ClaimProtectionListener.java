package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.api.permission.AccessDecision;
import com.herasgarden.gardencore.api.permission.ContainerAccessAction;
import com.herasgarden.gardencore.api.permission.ContainerAccessPolicy;
import com.herasgarden.gardencore.api.permission.DoorAccessAction;
import com.herasgarden.gardencore.api.permission.DoorAccessPolicy;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class ClaimProtectionListener implements Listener {
    private final ClaimService claims;
    private final ClaimSessionManager sessions;

    public ClaimProtectionListener(ClaimService claims, ClaimSessionManager sessions) {
        this.claims = claims;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Claim claim = claims.findAt(event.getBlockPlaced().getLocation());
        if (claim != null && !claims.can(event.getPlayer(), claim, ClaimPermission.BUILD)) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "You cannot build here.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Claim claim = claims.findAt(event.getBlock().getLocation());
        if (claim == null) {
            return;
        }
        if (isContainer(event.getBlock()) && !claims.canManage(event.getPlayer(), claim)) {
            AccessDecision decision = containerDecision(event.getPlayer(), event.getBlock(), ContainerAccessAction.BREAK);
            if (decision == AccessDecision.ALLOW) {
                return;
            }
            if (decision == AccessDecision.DENY) {
                event.setCancelled(true);
                Messages.send(event.getPlayer(), "You cannot break this container.");
                return;
            }
        }
        if (isDoor(event.getBlock()) && !claims.canManage(event.getPlayer(), claim)) {
            AccessDecision decision = doorDecision(event.getPlayer(), event.getBlock(), DoorAccessAction.BREAK);
            if (decision == AccessDecision.ALLOW) {
                return;
            }
            if (decision == AccessDecision.DENY) {
                event.setCancelled(true);
                Messages.send(event.getPlayer(), "You cannot break this door.");
                return;
            }
        }
        ClaimPermission permission = isContainer(event.getBlock())
                ? ClaimPermission.CONTAINER_BREAK : ClaimPermission.BREAK;
        if (!claims.can(event.getPlayer(), claim, permission)) {
            event.setCancelled(true);
            if (isContainer(event.getBlock())) {
                Messages.send(event.getPlayer(), "You cannot break this container.");
            } else if (isDoor(event.getBlock())) {
                Messages.send(event.getPlayer(), "You cannot break this door.");
            } else {
                Messages.send(event.getPlayer(), "You cannot break blocks here.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (sessions.has(event.getPlayer())) {
            return;
        }

        Block block = event.getClickedBlock();
        Claim claim = claims.findAt(block.getLocation());
        if (claim == null) {
            return;
        }

        ClaimPermission permission = null;
        if (isContainer(block)) {
            if (!claims.canManage(event.getPlayer(), claim)) {
                AccessDecision decision = containerDecision(event.getPlayer(), block, ContainerAccessAction.OPEN);
                if (decision == AccessDecision.ALLOW) {
                    return;
                }
                if (decision == AccessDecision.DENY) {
                    event.setCancelled(true);
                    Messages.send(event.getPlayer(), "You cannot open this container.");
                    return;
                }
            }
            permission = ClaimPermission.CONTAINER_OPEN;
        } else if (isDoor(block)) {
            if (!claims.canManage(event.getPlayer(), claim)) {
                AccessDecision decision = doorDecision(event.getPlayer(), block, DoorAccessAction.USE);
                if (decision == AccessDecision.ALLOW) {
                    return;
                }
                if (decision == AccessDecision.DENY) {
                    event.setCancelled(true);
                    Messages.send(event.getPlayer(), "You cannot use this door.");
                    return;
                }
            }
            permission = ClaimPermission.DOOR_USE;
        }

        if (permission != null && !claims.can(event.getPlayer(), claim, permission)) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), permission == ClaimPermission.DOOR_USE
                    ? "You cannot use this door." : "You cannot open this container.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Block block = holderBlock(event.getInventory().getHolder());
        if (block == null || !isContainer(block)) {
            return;
        }
        Claim claim = claims.findAt(block.getLocation());
        if (claim == null || claims.canManage(player, claim)) {
            return;
        }
        AccessDecision decision = containerDecision(player, block, ContainerAccessAction.OPEN);
        if (decision == AccessDecision.ALLOW) {
            return;
        }
        if (decision == AccessDecision.DENY || !claims.can(player, claim, ClaimPermission.CONTAINER_OPEN)) {
            event.setCancelled(true);
            Messages.send(player, "You cannot open this container.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Block block = holderBlock(top.getHolder());
        if (block == null || !isContainer(block)) {
            return;
        }
        Claim claim = claims.findAt(block.getLocation());
        if (claim == null || claims.canManage(player, claim)) {
            return;
        }

        int topSize = top.getSize();
        boolean clickedTop = event.getRawSlot() >= 0 && event.getRawSlot() < topSize;
        InventoryAction action = event.getAction();

        boolean taking = clickedTop && switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, DROP_ALL_SLOT, DROP_ONE_SLOT,
                    HOTBAR_MOVE_AND_READD, HOTBAR_SWAP, MOVE_TO_OTHER_INVENTORY -> true;
            default -> false;
        };
        boolean inserting = clickedTop && switch (action) {
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP -> true;
            default -> false;
        };
        if (!clickedTop && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            inserting = true;
        }

        if (taking && !claims.canManage(player, claim)
                && denied(player, block, claim, ContainerAccessAction.TAKE, ClaimPermission.CONTAINER_TAKE)) {
            event.setCancelled(true);
            Messages.send(player, "You cannot take items from this container.");
            return;
        }
        if (inserting && !claims.canManage(player, claim)
                && denied(player, block, claim, ContainerAccessAction.INSERT, ClaimPermission.CONTAINER_INSERT)) {
            event.setCancelled(true);
            Messages.send(player, "You cannot put items in this container.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Block block = holderBlock(top.getHolder());
        if (block == null || !isContainer(block)) {
            return;
        }
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize());
        if (!intoTop) {
            return;
        }
        Claim claim = claims.findAt(block.getLocation());
        if (claim != null && !claims.canManage(player, claim)
                && denied(player, block, claim, ContainerAccessAction.INSERT, ClaimPermission.CONTAINER_INSERT)) {
            event.setCancelled(true);
            Messages.send(player, "You cannot put items in this container.");
        }
    }

    private boolean denied(Player player, Block block, Claim claim,
                           ContainerAccessAction action, ClaimPermission fallback) {
        AccessDecision decision = containerDecision(player, block, action);
        if (decision == AccessDecision.ALLOW) {
            return false;
        }
        if (decision == AccessDecision.DENY) {
            return true;
        }
        return !claims.can(player, claim, fallback);
    }

    private AccessDecision containerDecision(Player player, Block block, ContainerAccessAction action) {
        RegisteredServiceProvider<ContainerAccessPolicy> registration =
                Bukkit.getServicesManager().getRegistration(ContainerAccessPolicy.class);
        if (registration == null || registration.getProvider() == null) {
            return AccessDecision.INHERIT;
        }
        try {
            AccessDecision decision = registration.getProvider().decide(player, block, action);
            return decision == null ? AccessDecision.INHERIT : decision;
        } catch (RuntimeException ignored) {
            return AccessDecision.INHERIT;
        }
    }

    private AccessDecision doorDecision(Player player, Block block, DoorAccessAction action) {
        RegisteredServiceProvider<DoorAccessPolicy> registration =
                Bukkit.getServicesManager().getRegistration(DoorAccessPolicy.class);
        if (registration == null || registration.getProvider() == null) {
            return AccessDecision.INHERIT;
        }
        try {
            AccessDecision decision = registration.getProvider().decide(player, block, action);
            return decision == null ? AccessDecision.INHERIT : decision;
        } catch (RuntimeException ignored) {
            return AccessDecision.INHERIT;
        }
    }

    private boolean isContainer(Block block) {
        Material type = block.getType();
        return type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL;
    }

    private boolean isDoor(Block block) {
        return Tag.DOORS.isTagged(block.getType()) || Tag.TRAPDOORS.isTagged(block.getType());
    }

    private Block holderBlock(InventoryHolder holder) {
        if (holder instanceof Container container) {
            return container.getBlock();
        }
        if (holder instanceof DoubleChest doubleChest) {
            Block left = holderBlock(doubleChest.getLeftSide());
            return left != null ? left : holderBlock(doubleChest.getRightSide());
        }
        return null;
    }
}
