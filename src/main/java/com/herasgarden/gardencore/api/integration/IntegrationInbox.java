package com.herasgarden.gardencore.api.integration;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Durable inbound command queue for trusted integrations.
 *
 * Commands are claimed before execution so a deploy or duplicate process
 * cannot execute the same moderation action twice.
 */
public interface IntegrationInbox {
    List<IntegrationCommand> claimPending(Collection<IntegrationCommandType> types, int limit, long staleClaimMillis) throws SQLException;

    void markProcessed(UUID commandId) throws SQLException;

    void release(UUID commandId, String error) throws SQLException;
}
