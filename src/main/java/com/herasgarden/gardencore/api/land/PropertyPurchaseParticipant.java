package com.herasgarden.gardencore.api.land;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Optional domain work committed inside the same SQL transaction as a property
 * purchase. The callback must not commit, roll back, or close the connection.
 */
@FunctionalInterface
public interface PropertyPurchaseParticipant {
    void apply(Connection connection, Context context) throws SQLException;

    static PropertyPurchaseParticipant none() {
        return (connection, context) -> { };
    }

    record Context(
            UUID propertyId,
            UUID claimId,
            UUID buyerId,
            String buyerKind,
            UUID sellerId,
            long price
    ) {
    }
}
