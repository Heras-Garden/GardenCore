package com.herasgarden.gardencore.api.land;

import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transitional property mutation contract used while the SQL implementation
 * is extracted from GardenCore into GardenLands.
 *
 * GardenLands owns player-facing property commands and permission/location
 * checks. Implementations own durable property mutation.
 */
public interface PropertyManagementService {
    Optional<PropertyAddress> find(UUID propertyId);

    Optional<PropertyAddress> findByClaim(UUID claimId);

    Optional<PropertyAddress> findByDisplayAddress(String road, String lineTwo);

    List<PropertyAddress> all();

    Optional<PropertySignBinding> signAt(PropertyBlockPosition position);

    Optional<PropertyAddress> propertyForMailbox(PropertyBlockPosition position);

    List<PropertySignBinding> signsFor(UUID propertyId);

    boolean apartmentSetupComplete(UUID propertyId);

    void bindSign(
            UUID propertyId,
            PropertyBlockPosition position,
            UUID createdBy,
            PropertySignKind kind
    ) throws SQLException;

    void removeSign(PropertyBlockPosition position) throws SQLException;

    void setMailbox(UUID propertyId, PropertyBlockPosition position) throws SQLException;

    void refreshSigns(UUID propertyId);

    PropertyAddress register(
            UUID claimId,
            String road,
            String number,
            String unitLabel
    ) throws SQLException;

    PropertyAddress updateAddress(
            UUID propertyId,
            String road,
            String number,
            String unitLabel
    ) throws SQLException;

    PropertyAddress setForSale(UUID propertyId, long price) throws SQLException;

    String buyerAudience(UUID propertyId);

    PropertyAddress setBuyerAudience(UUID propertyId, String audience) throws SQLException;

    boolean inheritAccount(
            UUID propertyId,
            UUID expectedOwnerId,
            UUID newOwnerId,
            String newOwnerName,
            String newOwnerKind
    ) throws SQLException;

    PropertyAddress takeOffMarket(UUID propertyId) throws SQLException;

    void delete(UUID propertyId) throws SQLException;

    PropertyPurchaseResult purchase(Player buyer, UUID propertyId);

    /**
     * Purchase a listed property using a Garden account that is not necessarily
     * backed by an online Bukkit Player (for example a Society citizen).
     */
    default PropertyPurchaseResult purchaseAccount(
            UUID buyerId,
            String buyerName,
            String buyerKind,
            UUID propertyId
    ) {
        return purchaseAccount(
                buyerId, buyerName, buyerKind, propertyId, PropertyPurchaseParticipant.none());
    }

    PropertyPurchaseResult purchaseAccount(
            UUID buyerId,
            String buyerName,
            String buyerKind,
            UUID propertyId,
            PropertyPurchaseParticipant participant
    );
}
