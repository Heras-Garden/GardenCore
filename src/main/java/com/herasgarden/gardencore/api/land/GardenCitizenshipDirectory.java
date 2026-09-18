package com.herasgarden.gardencore.api.land;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GardenCitizenshipDirectory {
    Optional<UUID> territoryClaimOf(UUID playerId);

    void setCitizenship(UUID playerId, UUID territoryClaimId) throws SQLException;

    boolean clearCitizenship(UUID playerId) throws SQLException;

    List<UUID> citizens(UUID territoryClaimId);
}
