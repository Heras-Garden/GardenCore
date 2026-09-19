package com.herasgarden.gardencore.api.social;

import java.util.Set;
import java.util.UUID;

public interface MarriageDirectory {
    Set<UUID> partners(UUID playerId);

    default boolean arePartners(UUID first, UUID second) {
        return first != null && second != null && partners(first).contains(second);
    }
}
