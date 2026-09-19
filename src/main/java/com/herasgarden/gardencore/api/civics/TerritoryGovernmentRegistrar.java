package com.herasgarden.gardencore.api.civics;

import com.herasgarden.gardencore.claim.GovernmentType;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public interface TerritoryGovernmentRegistrar {
    UUID createForTerritory(Player founder, UUID territoryClaimId, String territoryName, GovernmentType governmentType)
            throws SQLException;
}
