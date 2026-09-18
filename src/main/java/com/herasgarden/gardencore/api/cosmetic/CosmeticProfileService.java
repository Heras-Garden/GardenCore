package com.herasgarden.gardencore.api.cosmetic;

import java.util.UUID;

public interface CosmeticProfileService {
    CosmeticChatProfile chatProfile(UUID playerId);

    boolean hasEntitlement(UUID playerId, String entitlementKey);
}
