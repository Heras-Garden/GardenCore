package com.herasgarden.gardencore.platform.land;

import com.herasgarden.gardencore.api.land.PropertyAddress;
import com.herasgarden.gardencore.api.land.PropertyBlockPosition;
import com.herasgarden.gardencore.api.land.PropertyManagementService;
import com.herasgarden.gardencore.api.land.PropertyPurchaseParticipant;
import com.herasgarden.gardencore.api.land.PropertyPurchaseResult;
import com.herasgarden.gardencore.api.land.PropertySignBinding;
import com.herasgarden.gardencore.api.land.PropertySignKind;
import com.herasgarden.gardencore.claim.Claim;
import com.herasgarden.gardencore.claim.ClaimService;
import com.herasgarden.gardencore.claim.ClaimType;
import com.herasgarden.gardencore.property.BlockPosition;
import com.herasgarden.gardencore.property.PropertyService;
import com.herasgarden.gardencore.property.PropertySignLink;
import com.herasgarden.gardencore.property.PropertySignStyle;
import com.herasgarden.gardencore.property.SqlProperty;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CorePropertyManagementService implements PropertyManagementService {
    private final PropertyService properties;
    private final ClaimService claims;

    public CorePropertyManagementService(PropertyService properties, ClaimService claims) {
        this.properties = properties;
        this.claims = claims;
    }

    @Override
    public Optional<PropertyAddress> find(UUID propertyId) {
        return Optional.ofNullable(properties.get(propertyId)).map(this::view);
    }

    @Override
    public Optional<PropertyAddress> findByClaim(UUID claimId) {
        return Optional.ofNullable(properties.getByClaim(claimId)).map(this::view);
    }

    @Override
    public Optional<PropertyAddress> findByDisplayAddress(String road, String lineTwo) {
        return Optional.ofNullable(properties.getByDisplayAddress(road, lineTwo)).map(this::view);
    }

    @Override
    public List<PropertyAddress> all() {
        return properties.all().stream().map(this::view).toList();
    }

    @Override
    public Optional<PropertySignBinding> signAt(PropertyBlockPosition position) {
        if (position == null) return Optional.empty();
        return Optional.ofNullable(properties.getSign(internal(position))).map(this::binding);
    }

    @Override
    public Optional<PropertyAddress> propertyForMailbox(PropertyBlockPosition position) {
        if (position == null) return Optional.empty();
        return Optional.ofNullable(properties.propertyForMailbox(internal(position))).map(this::view);
    }

    @Override
    public List<PropertySignBinding> signsFor(UUID propertyId) {
        return properties.signsFor(propertyId).stream().map(this::binding).toList();
    }

    @Override
    public boolean apartmentSetupComplete(UUID propertyId) {
        SqlProperty property = properties.get(propertyId);
        return property != null && properties.isApartmentSetupComplete(property);
    }

    @Override
    public void bindSign(
            UUID propertyId,
            PropertyBlockPosition position,
            UUID createdBy,
            PropertySignKind kind
    ) throws SQLException {
        properties.bindSign(
                require(propertyId),
                internal(position),
                createdBy,
                PropertySignStyle.valueOf(kind.name())
        );
    }

    @Override
    public void removeSign(PropertyBlockPosition position) throws SQLException {
        properties.removeSign(internal(position));
    }

    @Override
    public void setMailbox(UUID propertyId, PropertyBlockPosition position) throws SQLException {
        properties.setMailbox(require(propertyId), internal(position));
    }

    @Override
    public void refreshSigns(UUID propertyId) {
        SqlProperty property = properties.get(propertyId);
        if (property != null) {
            properties.refreshSigns(property);
        }
    }

    @Override
    public PropertyAddress register(UUID claimId, String road, String number, String unitLabel)
            throws SQLException {
        Claim claim = claims.get(claimId);
        if (claim == null) {
            throw new IllegalArgumentException("That Garden claim no longer exists.");
        }
        if (claim.type() == ClaimType.TERRITORY
                || claim.type() == ClaimType.DISTRICT
                || claim.type() == ClaimType.PROTECTED) {
            throw new IllegalArgumentException("Territory, district, and protected claims do not use property addresses.");
        }
        if (claim.type() == ClaimType.UNIT
                && claim.tag() == com.herasgarden.gardencore.claim.ClaimTag.HOTEL_ROOM) {
            throw new IllegalArgumentException("Hotel rooms are temporary units and do not have registered addresses.");
        }
        return view(properties.register(claim, road, number, unitLabel));
    }

    @Override
    public PropertyAddress updateAddress(
            UUID propertyId,
            String road,
            String number,
            String unitLabel
    ) throws SQLException {
        SqlProperty property = require(propertyId);
        properties.updateAddress(property, road, number, unitLabel);
        return view(property);
    }

    @Override
    public PropertyAddress setForSale(UUID propertyId, long price) throws SQLException {
        SqlProperty property = require(propertyId);
        properties.setForSale(property, price);
        return view(property);
    }

    @Override
    public String buyerAudience(UUID propertyId) {
        SqlProperty property = require(propertyId);
        return property.buyerAudience();
    }

    @Override
    public PropertyAddress setBuyerAudience(UUID propertyId, String audience) throws SQLException {
        SqlProperty property = require(propertyId);
        properties.setBuyerAudience(property, audience);
        return view(property);
    }

    @Override
    public boolean inheritAccount(UUID propertyId, UUID expectedOwnerId, UUID newOwnerId,
                                  String newOwnerName, String newOwnerKind) throws SQLException {
        return properties.inheritAccount(require(propertyId), expectedOwnerId, newOwnerId, newOwnerKind);
    }

    @Override
    public PropertyAddress takeOffMarket(UUID propertyId) throws SQLException {
        SqlProperty property = require(propertyId);
        properties.takeOffMarket(property);
        return view(property);
    }

    @Override
    public void delete(UUID propertyId) throws SQLException {
        properties.deleteProperty(require(propertyId));
    }

    @Override
    public PropertyPurchaseResult purchase(Player buyer, UUID propertyId) {
        SqlProperty property = properties.get(propertyId);
        if (property == null) {
            return new PropertyPurchaseResult(false, "That property no longer exists.", 0L);
        }
        PropertyService.PurchaseResult result = properties.purchase(buyer, property);
        return new PropertyPurchaseResult(result.success(), result.message(), result.price());
    }

    @Override
    public PropertyPurchaseResult purchaseAccount(
            UUID buyerId,
            String buyerName,
            String buyerKind,
            UUID propertyId,
            PropertyPurchaseParticipant participant
    ) {
        SqlProperty property = properties.get(propertyId);
        if (property == null) {
            return new PropertyPurchaseResult(false, "That property no longer exists.", 0L);
        }
        PropertyService.PurchaseResult result =
                properties.purchaseAccount(buyerId, buyerName, buyerKind, property, participant);
        return new PropertyPurchaseResult(result.success(), result.message(), result.price());
    }

    private SqlProperty require(UUID propertyId) {
        SqlProperty property = properties.get(propertyId);
        if (property == null) {
            throw new IllegalArgumentException("That property no longer exists.");
        }
        return property;
    }

    private PropertySignBinding binding(PropertySignLink link) {
        return new PropertySignBinding(
                external(link.position()),
                link.propertyId(),
                link.createdBy(),
                PropertySignKind.valueOf(link.signStyle().name())
        );
    }

    private BlockPosition internal(PropertyBlockPosition position) {
        return new BlockPosition(
                position.worldId(),
                position.worldName(),
                position.x(),
                position.y(),
                position.z()
        );
    }

    private PropertyBlockPosition external(BlockPosition position) {
        return new PropertyBlockPosition(
                position.worldId(),
                position.worldName(),
                position.x(),
                position.y(),
                position.z()
        );
    }

    private PropertyAddress view(SqlProperty property) {
        return new PropertyAddress(
                property.id(),
                property.claimId(),
                property.scopeKey(),
                property.road(),
                property.number(),
                property.unit(),
                property.price(),
                property.forSale()
        );
    }
}
