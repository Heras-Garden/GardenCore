package com.herasgarden.gardencore.api.claim;

import java.util.Optional;
import java.util.UUID;

/**
 * Optional domain policy that can temporarily block claim ownership transfers.
 *
 * An empty result allows the transfer. A present message explains why the
 * transfer/listing must wait.
 */
public interface ClaimTransferPolicy {
    Optional<String> blockReason(UUID claimId);
}
