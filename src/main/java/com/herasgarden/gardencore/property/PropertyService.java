package com.herasgarden.gardencore.property;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.claim.Claim;
import com.herasgarden.gardencore.claim.ClaimOwnerType;
import com.herasgarden.gardencore.claim.ClaimService;
import com.herasgarden.gardencore.claim.ClaimType;
import com.herasgarden.gardencore.claim.ClaimTag;
import com.herasgarden.gardencore.api.integration.IntegrationEventType;
import com.herasgarden.gardencore.api.order.GardenOrder;
import com.herasgarden.gardencore.api.order.OrderState;
import com.herasgarden.gardencore.api.order.OrderType;
import com.herasgarden.gardencore.database.PropertyRepository;
import com.herasgarden.gardencore.organization.Organization;
import com.herasgarden.gardencore.organization.OrganizationService;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PropertyService {
    private final GardenCore plugin;
    private final ClaimService claims;
    private final PropertyRepository repository;
    private final Economy economy;
    private final OrganizationService organizations;

    private final Map<UUID, SqlProperty> byId = new ConcurrentHashMap<>();
    private final Map<UUID, SqlProperty> byClaim = new ConcurrentHashMap<>();
    private final Map<String, SqlProperty> byAddress = new ConcurrentHashMap<>();
    private final Map<String, PropertySignLink> signs = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPosition> mailboxes = new ConcurrentHashMap<>();
    private final Set<UUID> purchasesInProgress = ConcurrentHashMap.newKeySet();

    public PropertyService(GardenCore plugin, ClaimService claims, PropertyRepository repository, Economy economy,
                           OrganizationService organizations) {
        this.plugin = plugin;
        this.claims = claims;
        this.repository = repository;
        this.economy = economy;
        this.organizations = organizations;
    }

    public void load() throws SQLException {
        byId.clear();
        byClaim.clear();
        byAddress.clear();
        signs.clear();
        mailboxes.clear();

        for (SqlProperty property : repository.loadProperties()) {
            byId.put(property.id(), property);
            byClaim.put(property.claimId(), property);
            byAddress.put(property.addressKey(), property);
        }
        for (PropertySignLink sign : repository.loadSigns()) {
            signs.put(sign.position().key(), sign);
        }
        mailboxes.putAll(repository.loadMailboxes());
    }

    public Collection<SqlProperty> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public SqlProperty get(UUID id) { return byId.get(id); }
    public SqlProperty getByClaim(UUID claimId) { return byClaim.get(claimId); }

    public SqlProperty getByAddress(String road, String number) {
        return byAddress.get(SqlProperty.addressKey("global", road, number, null));
    }

    public SqlProperty getByAddress(String road, String number, String unit) {
        return byAddress.get(SqlProperty.addressKey("global", road, number, unit));
    }

    public SqlProperty getByDisplayAddress(String road, String lineTwo) {
        if (road == null || lineTwo == null) {
            return null;
        }
        String roadKey = SqlProperty.normalize(road);
        String secondKey = SqlProperty.normalize(lineTwo);
        return byId.values().stream()
                .filter(property -> SqlProperty.normalize(property.road()).equals(roadKey))
                .filter(property -> SqlProperty.normalize(displayLineTwo(property)).equals(secondKey))
                .findFirst()
                .orElse(null);
    }

    public String displayLineTwo(SqlProperty property) {
        return property.unit() == null ? property.number() : property.number() + " " + property.unit();
    }

    public PropertySignLink getSign(BlockPosition position) {
        return signs.get(position.key());
    }

    public List<PropertySignLink> signsFor(UUID propertyId) {
        return signs.values().stream().filter(link -> link.propertyId().equals(propertyId)).toList();
    }

    public List<PropertySignLink> signsFor(UUID propertyId, PropertySignStyle style) {
        return signs.values().stream()
                .filter(link -> link.propertyId().equals(propertyId) && link.signStyle() == style)
                .toList();
    }

    public boolean hasSignStyle(UUID propertyId, PropertySignStyle style) {
        return signs.values().stream()
                .anyMatch(link -> link.propertyId().equals(propertyId) && link.signStyle() == style);
    }

    public BlockPosition mailbox(UUID propertyId) {
        return mailboxes.get(propertyId);
    }

    public SqlProperty register(Claim claim, String road, String number, String unit) throws SQLException {
        if (claim == null) {
            throw new IllegalArgumentException("A property requires a claim.");
        }
        if (byClaim.containsKey(claim.id())) {
            throw new IllegalArgumentException("This claim already has a property address.");
        }
        String key = SqlProperty.addressKey("global", road, number, unit);
        if (byAddress.containsKey(key)) {
            throw new IllegalArgumentException("That address already exists.");
        }

        SqlProperty property = new SqlProperty(UUID.randomUUID(), claim.id(), "global",
                road, number, unit, 0L, false, Instant.now());
        repository.insert(property);
        byId.put(property.id(), property);
        byClaim.put(property.claimId(), property);
        byAddress.put(property.addressKey(), property);
        return property;
    }

    public void updateAddress(SqlProperty property, String road, String number, String unit) throws SQLException {
        if (property == null || road == null || road.isBlank() || number == null || number.isBlank()) {
            throw new IllegalArgumentException("The property address needs a road and number.");
        }
        String newKey = SqlProperty.addressKey(property.scopeKey(), road, number, unit);
        SqlProperty existing = byAddress.get(newKey);
        if (existing != null && !existing.id().equals(property.id())) {
            throw new IllegalArgumentException("That address already exists.");
        }
        String oldKey = property.addressKey();
        repository.updateAddress(property, road.trim(), number.trim(), unit == null || unit.isBlank() ? null : unit.trim());
        byAddress.remove(oldKey);
        property.setAddress(road, number, unit);
        byAddress.put(property.addressKey(), property);
        refreshSigns(property);
    }

    public void setForSale(SqlProperty property, long price) throws SQLException {
        if (price <= 0) {
            throw new IllegalArgumentException("Sale price must be greater than zero.");
        }
        Optional<String> blocked = claims.transferBlockReason(property.claimId());
        if (blocked.isPresent()) {
            throw new IllegalArgumentException(blocked.get());
        }
        repository.setSale(property.id(), price, true);
        property.setPrice(price);
        property.setForSale(true);
        refreshSigns(property);
    }

    public void takeOffMarket(SqlProperty property) throws SQLException {
        repository.setSale(property.id(), property.price(), false);
        property.setForSale(false);
        refreshSigns(property);
    }

    public void bindSign(SqlProperty property, BlockPosition position, UUID createdBy) throws SQLException {
        bindSign(property, position, createdBy, PropertySignStyle.PROPERTY_MAILBOX);
    }

    public void bindSign(SqlProperty property, BlockPosition position, UUID createdBy, PropertySignStyle style) throws SQLException {
        if (hasSignStyle(property.id(), style)) {
            throw new IllegalArgumentException("This property already has its " + signLabel(style) + " sign.");
        }
        PropertySignLink link = new PropertySignLink(position, property.id(), createdBy, style.name());
        repository.bindSign(link);
        signs.put(position.key(), link);
    }

    private String signLabel(PropertySignStyle style) {
        return switch (style) {
            case PROPERTY_MAILBOX -> "property/mailbox";
            case APARTMENT_UNIT -> "apartment room";
            case APARTMENT_MAILBOX -> "apartment mailbox";
        };
    }

    /**
     * Linked property signs are permanent while the property exists. This method
     * remains for cleanup/internal migrations, not normal player sign breaking.
     */
    public void removeSign(BlockPosition position) throws SQLException {
        repository.removeSign(position);
        signs.remove(position.key());
    }

    public void setMailbox(SqlProperty property, BlockPosition position) throws SQLException {
        repository.setMailbox(property.id(), position);
        mailboxes.put(property.id(), position);
    }

    public SqlProperty propertyForMailbox(BlockPosition position) {
        if (position == null) {
            return null;
        }
        return mailboxes.entrySet().stream()
                .filter(entry -> entry.getValue().key().equals(position.key()))
                .map(entry -> byId.get(entry.getKey()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public boolean isApartmentSetupComplete(SqlProperty property) {
        if (property == null) {
            return false;
        }
        Claim claim = claims.get(property.claimId());
        if (claim == null || claim.type() != ClaimType.UNIT || claim.tag() != ClaimTag.APARTMENT) {
            return true;
        }
        return hasSignStyle(property.id(), PropertySignStyle.APARTMENT_UNIT)
                && hasSignStyle(property.id(), PropertySignStyle.APARTMENT_MAILBOX)
                && mailbox(property.id()) != null;
    }

    public void deleteProperty(SqlProperty property) throws SQLException {
        if (property == null) {
            throw new IllegalArgumentException("That property no longer exists.");
        }
        Claim claim = claims.get(property.claimId());
        if (claim == null) {
            throw new IllegalArgumentException("The claim linked to this property is missing.");
        }
        if (claims.hasChildren(claim)) {
            throw new IllegalArgumentException("This property contains child claims. Delete or move those properties first.");
        }
        Optional<String> blocked = claims.transferBlockReason(property.claimId());
        if (blocked.isPresent()) {
            throw new IllegalArgumentException(blocked.get());
        }

        List<PropertySignLink> linkedSigns = signsFor(property.id());
        repository.deletePropertyAndClaim(property.id(), property.claimId());

        byId.remove(property.id());
        byClaim.remove(property.claimId());
        byAddress.remove(property.addressKey());
        mailboxes.remove(property.id());
        linkedSigns.forEach(link -> signs.remove(link.position().key()));
        claims.forgetDeletedClaim(property.claimId());

        // The sign is part of the property record. Remove it only after the SQL
        // deletion succeeds so a failed delete never leaves an untracked property.
        for (PropertySignLink link : linkedSigns) {
            World world = Bukkit.getWorld(link.position().worldId());
            if (world == null) {
                continue;
            }
            world.getChunkAt(link.position().x() >> 4, link.position().z() >> 4).load();
            if (world.getBlockAt(link.position().x(), link.position().y(), link.position().z()).getState() instanceof Sign) {
                world.getBlockAt(link.position().x(), link.position().y(), link.position().z()).setType(Material.AIR, false);
            }
        }
    }

    public PurchaseResult purchase(Player buyer, SqlProperty property) {
        if (!purchasesInProgress.add(property.id())) {
            return PurchaseResult.failure("Someone is already purchasing this property.");
        }
        try {
            if (!property.forSale()) {
                return PurchaseResult.failure("This property is not for sale.");
            }
            Claim claim = claims.get(property.claimId());
            if (claim == null) {
                return PurchaseResult.failure("The claim linked to this property is missing.");
            }
            Optional<String> blocked = claims.transferBlockReason(property.claimId());
            if (blocked.isPresent()) {
                return PurchaseResult.failure(blocked.get());
            }
            if (claim.type() == ClaimType.UNIT && claim.tag() == ClaimTag.APARTMENT && !isApartmentSetupComplete(property)) {
                return PurchaseResult.failure("This apartment is not ready for purchase until both its room sign and mailbox sign are linked.");
            }
            if (claim.ownerType() != ClaimOwnerType.PLAYER) {
                return PurchaseResult.failure("Government and company property sales will be enabled with organization treasuries.");
            }

            UUID sellerId = claim.ownerId();
            if (sellerId.equals(buyer.getUniqueId())) {
                return PurchaseResult.failure("You already own this property.");
            }
            if (!economy.has(buyer, property.price())) {
                long missing = Math.max(1L, (long) Math.ceil(property.price() - economy.getBalance(buyer)));
                return PurchaseResult.failure("You need " + missing + " more ⟡ Obols.");
            }

            GardenOrder order;
            try {
                order = plugin.orders().create(
                        OrderType.PROPERTY_PURCHASE,
                        buyer.getUniqueId(),
                        "PLAYER",
                        sellerId.toString(),
                        property.price(),
                        "gardenlands.property-sign",
                        property.id().toString(),
                        "{\"propertyUuid\":\"" + property.id()
                                + "\",\"claimUuid\":\"" + property.claimId() + "\"}"
                );
                plugin.orders().transition(order.id(), OrderState.READY, "Property sign purchase prepared");
                plugin.orders().transition(order.id(), OrderState.AWAITING_CONFIRMATION,
                        "Player confirmed property sign purchase");
                plugin.orders().transition(order.id(), OrderState.PAYMENT_PENDING,
                        "Collecting property purchase payment");
            } catch (SQLException exception) {
                plugin.getLogger().warning("Could not prepare property purchase order for "
                        + property.id() + ": " + exception.getMessage());
                return PurchaseResult.failure("The property purchase could not be prepared right now.");
            }

            EconomyResponse withdrawal = economy.withdrawPlayer(buyer, property.price());
            if (!withdrawal.transactionSuccess()) {
                transitionOrder(order.id(), OrderState.PAYMENT_FAILED, "Buyer payment could not be withdrawn");
                return PurchaseResult.failure("The payment could not be completed.");
            }
            transitionOrder(order.id(), OrderState.PAID, "Property purchase paid");
            transitionOrder(order.id(), OrderState.FULFILLING, "Transferring property ownership");

            ClaimOwnerType oldType = claim.ownerType();
            UUID oldOwner = claim.ownerId();
            try {
                repository.completeSale(property, ClaimOwnerType.PLAYER, buyer.getUniqueId());
                claim.setOwner(ClaimOwnerType.PLAYER, buyer.getUniqueId());
                property.setForSale(false);
            } catch (SQLException exception) {
                EconomyResponse refund = economy.depositPlayer(buyer, property.price());
                transitionOrder(order.id(),
                        refund.transactionSuccess() ? OrderState.REFUNDED : OrderState.FULFILLMENT_FAILED,
                        refund.transactionSuccess()
                                ? "Property transfer failed; buyer payment returned"
                                : "Property transfer failed and automatic buyer refund failed");
                plugin.getLogger().severe("Property sale database transaction failed for " + property.id() + ": "
                        + exception.getMessage());
                return PurchaseResult.failure(refund.transactionSuccess()
                        ? "The property could not be transferred. Your Obols were returned."
                        : "The property transfer failed and needs administrator review.");
            }

            OfflinePlayer seller = Bukkit.getOfflinePlayer(sellerId);
            EconomyResponse sellerPayment = economy.depositPlayer(seller, property.price());
            if (!sellerPayment.transactionSuccess()) {
                try {
                    repository.rollbackSale(property, oldType, oldOwner);
                    claim.setOwner(oldType, oldOwner);
                    property.setForSale(true);
                    EconomyResponse refund = economy.depositPlayer(buyer, property.price());
                    transitionOrder(order.id(),
                            refund.transactionSuccess() ? OrderState.REFUNDED : OrderState.FULFILLMENT_FAILED,
                            refund.transactionSuccess()
                                    ? "Seller payment failed; ownership rolled back and buyer refunded"
                                    : "Seller payment failed; ownership rolled back but buyer refund failed");
                } catch (SQLException rollbackFailure) {
                    transitionOrder(order.id(), OrderState.FULFILLMENT_FAILED,
                            "Seller payment failed and ownership rollback requires admin review");
                    plugin.getLogger().severe("CRITICAL: property sale rollback failed for " + property.id()
                            + ". Manual admin recovery is required: " + rollbackFailure.getMessage());
                    return PurchaseResult.failure("The sale needs administrator review. No additional action should be taken.");
                }
                return PurchaseResult.failure("The seller could not be paid. The sale was cancelled and your Obols were returned.");
            }

            refreshSigns(property);
            transitionOrder(order.id(), OrderState.COMPLETED, "Property ownership transferred and seller paid");
            try {
                plugin.integrations().publish(
                        IntegrationEventType.PROPERTY_SOLD,
                        "property",
                        property.id().toString(),
                        "{\"propertyUuid\":\"" + property.id()
                                + "\",\"claimUuid\":\"" + property.claimId()
                                + "\",\"buyerUuid\":\"" + buyer.getUniqueId()
                                + "\",\"sellerUuid\":\"" + sellerId
                                + "\",\"amount\":" + property.price() + "}"
                );
            } catch (SQLException exception) {
                plugin.getLogger().warning("Property " + property.id()
                        + " sold, but its Iris event could not be queued: " + exception.getMessage());
            }
            return PurchaseResult.success(property.price());
        } finally {
            purchasesInProgress.remove(property.id());
        }
    }

    private void transitionOrder(UUID orderId, OrderState state, String detail) {
        try {
            plugin.orders().transition(orderId, state, detail);
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not journal property order " + orderId + " -> "
                    + state + ": " + exception.getMessage());
        }
    }

    public void refreshSigns(SqlProperty property) {
        for (PropertySignLink link : signs.values()) {
            if (!link.propertyId().equals(property.id())) {
                continue;
            }
            World world = Bukkit.getWorld(link.position().worldId());
            if (world == null) {
                continue;
            }
            int chunkX = link.position().x() >> 4;
            int chunkZ = link.position().z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }
            if (!(world.getBlockAt(link.position().x(), link.position().y(), link.position().z()).getState() instanceof Sign sign)) {
                continue;
            }
            applySignText(sign, property, link.signStyle());
            sign.update(true, false);
        }
    }

    public void applySignText(Sign sign, SqlProperty property) {
        PropertySignStyle style = claims.get(property.claimId()) != null
                && claims.get(property.claimId()).type() == ClaimType.UNIT
                && claims.get(property.claimId()).tag() == ClaimTag.APARTMENT
                ? PropertySignStyle.APARTMENT_UNIT : PropertySignStyle.PROPERTY_MAILBOX;
        applySignText(sign, property, style);
    }

    public void applySignText(Sign sign, SqlProperty property, PropertySignStyle style) {
        Claim claim = claims.get(property.claimId());
        String owner = claim == null ? "Unavailable" : ownerDisplay(claim);
        if (style == PropertySignStyle.APARTMENT_MAILBOX) {
            sign.setLine(0, property.unit() == null ? property.number() : property.unit());
            sign.setLine(1, property.forSale() ? "⟡ " + property.price() : owner);
            sign.setLine(2, "");
            sign.setLine(3, "");
            return;
        }
        sign.setLine(0, property.road());
        sign.setLine(1, displayLineTwo(property));
        sign.setLine(2, property.forSale() ? "⟡ " + property.price() : owner);
        sign.setLine(3, "");
    }

    public String ownerDisplay(Claim claim) {
        if (claim.ownerType() == ClaimOwnerType.PLAYER) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(claim.ownerId());
            String name = player.getName();
            return name == null || name.isBlank() ? claim.ownerId().toString().substring(0, 8) : name;
        }
        Organization organization = organizations.get(claim.ownerId());
        if (organization != null) {
            return organization.name();
        }
        return claim.ownerType() == ClaimOwnerType.COMPANY ? "Company" : "Government";
    }

    public record PurchaseResult(boolean success, String message, long price) {
        public static PurchaseResult success(long price) {
            return new PurchaseResult(true, "Property purchased for ⟡ " + price + ".", price);
        }

        public static PurchaseResult failure(String message) {
            return new PurchaseResult(false, message, 0L);
        }
    }
}
