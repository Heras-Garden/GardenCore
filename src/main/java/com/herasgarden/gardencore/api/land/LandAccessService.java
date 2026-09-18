package com.herasgarden.gardencore.api.land;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Public land-access view implemented by GardenLands.
 *
 * Other Garden modules can ask whether a player manages the land containing a
 * block without reading GardenLands tables or depending on its internals.
 */
public interface LandAccessService {
    boolean canManage(Player player, Block block);

    Optional<UUID> claimIdAt(Block block);

    Optional<String> claimTypeAt(Block block);
}
