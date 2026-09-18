package com.herasgarden.gardencore.platform.claim;

import com.herasgarden.gardencore.api.claim.ClaimOwnershipBridge;
import com.herasgarden.gardencore.claim.Claim;
import com.herasgarden.gardencore.claim.ClaimOwnerType;
import com.herasgarden.gardencore.claim.ClaimService;

import java.sql.SQLException;
import java.util.UUID;

public final class CoreClaimOwnershipBridge implements ClaimOwnershipBridge {
    private final ClaimService claims;

    public CoreClaimOwnershipBridge(ClaimService claims) {
        this.claims = claims;
    }

    @Override
    public synchronized boolean transferPlayerClaim(UUID claimId, UUID expectedOwner, UUID newOwner) throws SQLException {
        Claim claim = claims.get(claimId);
        if (claim == null || claim.ownerType() != ClaimOwnerType.PLAYER || !claim.ownerId().equals(expectedOwner)) {
            return false;
        }
        if (claims.transferBlockReason(claimId).isPresent()) {
            return false;
        }
        claims.transferOwner(claim, ClaimOwnerType.PLAYER, newOwner);
        return true;
    }
}
