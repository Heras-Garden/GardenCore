package com.herasgarden.gardencore.worldguard;

import com.herasgarden.gardencore.GardenCore;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Set;
import java.util.UUID;

public final class WorldGuardHook {
    private final GardenCore plugin;

    public WorldGuardHook(GardenCore plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean regionExists(World world, String regionId) {
        ProtectedRegion region = region(world, regionId);
        return region != null;
    }

    public boolean assignOwner(World world, String regionId, UUID owner) {
        if (!isAvailable() || world == null || owner == null) {
            return false;
        }

        RegionManager manager = manager(world);
        if (manager == null) {
            return false;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return false;
        }

        Set<UUID> previousOwners = Set.copyOf(region.getOwners().getUniqueIds());
        region.getOwners().clear();
        region.getOwners().addPlayer(owner);

        try {
            manager.saveChanges();
            return true;
        } catch (StorageException exception) {
            region.getOwners().clear();
            previousOwners.forEach(region.getOwners()::addPlayer);
            plugin.getLogger().severe("Could not save WorldGuard ownership for region " + regionId + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean clearOwners(World world, String regionId) {
        if (!isAvailable() || world == null) {
            return false;
        }
        RegionManager manager = manager(world);
        if (manager == null) {
            return false;
        }
        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            return false;
        }
        region.getOwners().clear();
        try {
            manager.saveChanges();
            return true;
        } catch (StorageException exception) {
            plugin.getLogger().severe("Could not clear WorldGuard ownership for region " + regionId + ": " + exception.getMessage());
            return false;
        }
    }

    private ProtectedRegion region(World world, String regionId) {
        RegionManager manager = manager(world);
        return manager == null ? null : manager.getRegion(regionId);
    }

    private RegionManager manager(World world) {
        if (!isAvailable() || world == null) {
            return null;
        }
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }
}
