package com.herasgarden.gardencore.api.land;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GardenTerritoryDirectory {
    Optional<TerritorySummary> findByName(String name);

    Optional<TerritorySummary> findByClaim(UUID claimId);

    List<TerritorySummary> list();

    boolean canManage(Player player, UUID territoryClaimId);
}
