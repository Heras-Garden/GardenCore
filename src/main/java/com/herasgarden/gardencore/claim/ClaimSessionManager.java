package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.api.civics.TerritoryGovernmentRegistrar;
import com.herasgarden.gardencore.territory.TerritoryService;
import com.herasgarden.gardencore.ui.ClaimChatUi;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimSessionManager {
    private final GardenCore plugin;
    private final ClaimService claimService;
    private final TerritoryService territoryService;
    private final ClaimProfileService profiles;
    private final Map<UUID, ClaimSession> sessions = new ConcurrentHashMap<>();

    public ClaimSessionManager(GardenCore plugin, ClaimService claimService, TerritoryService territoryService,
                               ClaimProfileService profiles) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.territoryService = territoryService;
        this.profiles = profiles;
    }

    public ClaimSession start(Player player, ClaimType type) {
        return start(player, type, ClaimOwnerType.PLAYER, player.getUniqueId(), null, null);
    }

    public ClaimSession start(Player player, ClaimType type, ClaimOwnerType ownerType, UUID ownerId,
                              ClaimTag tag, GovernmentType governmentType) {
        ClaimShape shape = type.defaultsToPolygon() ? ClaimShape.POLYGON : ClaimShape.RECTANGLE;
        ClaimSession session = new ClaimSession(player.getUniqueId(), player.getWorld(), type, ownerType, ownerId,
                tag, governmentType, shape, type.defaultsToFullHeight(), player.getLocation().getBlockY());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public ClaimSession get(Player player) { return sessions.get(player.getUniqueId()); }
    public boolean has(Player player) { return sessions.containsKey(player.getUniqueId()); }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
        Messages.send(player, "Claim cancelled.");
    }

    public ClaimValidation validation(Player player, ClaimSession session) {
        World world = Bukkit.getWorld(session.worldId());
        if (world == null) return ClaimValidation.invalid("The claim world is unavailable.");
        ClaimGeometry geometry = session.geometry(world);
        if (geometry == null) return ClaimValidation.invalid("Select more points first.");

        if (session.type() == ClaimType.UNIT && !session.heightConfigured()) {
            return ClaimValidation.invalid("Right-click the unit ceiling, then right-click the floor before confirming.");
        }
        if (session.type() == ClaimType.UNIT && session.tag() == null) {
            return ClaimValidation.invalid("Choose a unit tag before confirming.");
        }
        if (session.tag() != null && !session.tag().supports(session.type())) {
            return ClaimValidation.invalid("That tag cannot be used with this claim type.");
        }

        if (session.type() == ClaimType.TERRITORY) {
            if (session.ownerType() != ClaimOwnerType.PLAYER) {
                return ClaimValidation.invalid("New territories are founded by a player and become government-owned when confirmed.");
            }
            if (session.territoryName() == null || session.territoryName().isBlank()) {
                return ClaimValidation.invalid("Give the territory a name before confirming.");
            }
            if (session.territoryFlag() == null) return ClaimValidation.invalid("Set the territory banner flag before confirming.");
            if (session.governmentType() == null) return ClaimValidation.invalid("Choose a government type before confirming.");
            if (!territoryService.isNameAvailable(session.territoryName())) {
                return ClaimValidation.invalid("That territory name is already in use.");
            }
            RegisteredServiceProvider<TerritoryGovernmentRegistrar> registration =
                    Bukkit.getServicesManager().getRegistration(TerritoryGovernmentRegistrar.class);
            if (registration == null || registration.getProvider() == null) {
                return ClaimValidation.invalid("GardenCivics must be enabled before a territory can be founded.");
            }
        }

        if (session.parentId() == null) {
            ClaimType requiredParent = switch (session.type()) {
                case UNIT -> ClaimType.PROPERTY;
                case DISTRICT -> ClaimType.TERRITORY;
                default -> null;
            };
            Claim managedParent = requiredParent == null
                    ? claimService.findManagedParent(player, geometry)
                    : claimService.findManagedParent(player, geometry, requiredParent);
            if (managedParent != null) session.setParentId(managedParent.id());
        }

        if (session.type() == ClaimType.DISTRICT) {
            String name = session.claimName();
            if (name == null || name.isBlank()) {
                return ClaimValidation.invalid("Give the district a name with /claim name <name> before confirming.");
            }
            if (name.length() < 3 || name.length() > 48) {
                return ClaimValidation.invalid("District names must be between 3 and 48 characters.");
            }
            if (session.parentId() != null
                    && !claimService.isSiblingNameAvailable(session.parentId(), ClaimType.DISTRICT, name)) {
                return ClaimValidation.invalid("That district name is already in use inside this territory.");
            }
        }

        if (session.type() == ClaimType.UNIT && session.parentId() != null) {
            Claim parent = claimService.get(session.parentId());
            if (session.tag() == ClaimTag.APARTMENT
                    && (parent == null || parent.tag() != ClaimTag.APARTMENT_BUILDING)) {
                return ClaimValidation.invalid("Apartment units must be inside a property tagged apartment_building.");
            }
            if (session.tag() == ClaimTag.HOTEL_ROOM
                    && (parent == null || parent.tag() != ClaimTag.HOTEL)) {
                return ClaimValidation.invalid("Hotel-room units must be inside a property tagged hotel.");
            }
        }

        return claimService.validate(geometry, session.type(), session.ownerType(), session.ownerId(), session.parentId());
    }

    public void showPreview(Player player) {
        ClaimSession session = get(player);
        if (session == null) { Messages.send(player, "You are not creating a claim."); return; }
        World world = Bukkit.getWorld(session.worldId());
        ClaimGeometry geometry = world == null ? null : session.geometry(world);
        ClaimValidation validation = geometry == null ? null : validation(player, session);
        ClaimChatUi.sendPreviewSummary(player, session, geometry, validation);
        ClaimChatUi.sendSelectionControls(player, session, validation);
        if (session.type() == ClaimType.UNIT && session.closed()) {
            ClaimChatUi.sendApartmentHeightPrompt(player, session);
        } else if (session.type() == ClaimType.TERRITORY) {
            ClaimChatUi.sendTerritorySetup(player, session);
        }
    }

    public void confirm(Player player) {
        ClaimSession session = get(player);
        if (session == null) { Messages.send(player, "You are not creating a claim."); return; }
        if (!session.closed()) { Messages.send(player, "Close the claim by clicking the first point again."); return; }
        World world = Bukkit.getWorld(session.worldId());
        if (world == null) { Messages.send(player, "The claim world is unavailable."); return; }
        ClaimGeometry geometry = session.geometry(world);
        if (geometry == null) { Messages.send(player, "The claim is incomplete."); return; }

        ClaimValidation validation = validation(player, session);
        if (!validation.valid()) {
            Messages.send(player, validation.reason());
            ClaimChatUi.sendSelectionControls(player, session, validation);
            if (session.type() == ClaimType.TERRITORY) ClaimChatUi.sendTerritorySetup(player, session);
            return;
        }

        Claim claim = null;
        boolean territoryRegistered = false;
        try {
            String claimName = switch (session.type()) {
                case TERRITORY -> session.territoryName();
                case DISTRICT -> session.claimName();
                default -> null;
            };
            claim = claimService.create(session.type(), session.ownerType(), session.ownerId(), session.parentId(),
                    claimName, session.tag(), session.governmentType(), geometry, player.getUniqueId());

            if (session.type() == ClaimType.TERRITORY) {
                territoryService.register(claim, session.territoryName(), session.territoryFlag());
                territoryRegistered = true;
                RegisteredServiceProvider<TerritoryGovernmentRegistrar> registration =
                        Bukkit.getServicesManager().getRegistration(TerritoryGovernmentRegistrar.class);
                if (registration == null || registration.getProvider() == null) {
                    throw new IllegalArgumentException("GardenCivics became unavailable before confirmation.");
                }
                UUID governmentId = registration.getProvider().createForTerritory(
                        player, claim.id(), session.territoryName(), session.governmentType());
                claimService.transferOwner(claim, ClaimOwnerType.GOVERNMENT, governmentId);

            }

            sessions.remove(player.getUniqueId());
            String created = switch (session.type()) {
                case TERRITORY -> "Territory " + session.territoryName() + " and its "
                        + session.governmentType().displayName() + " government were created.";
                case DISTRICT -> "District " + session.claimName() + " created.";
                case UNIT -> "Unit claim created. ID: " + claim.id().toString().substring(0, 8) + ".";
                default -> "Claim created. ID: " + claim.id().toString().substring(0, 8) + ".";
            };
            Messages.send(player, created);
            if (session.type() == ClaimType.TERRITORY) {
                Messages.send(player, "Existing homes and properties inside the boundary keep their current owners.");
            } else if (session.type() == ClaimType.HOME) {
                Messages.send(player, "Register an address to make this home available through /home.");
            } else if (session.type() == ClaimType.UNIT && session.tag() == ClaimTag.APARTMENT) {
                Messages.send(player, "Register this apartment's address/unit and mailbox to make it available through /home.");
            } else if (session.type() == ClaimType.UNIT && session.tag() == ClaimTag.HOTEL_ROOM) {
                Messages.send(player, "Hotel rooms are addressless temporary units.");
            }
        } catch (SQLException | IllegalArgumentException exception) {
            if (claim != null) {
                try {
                    if (territoryRegistered) territoryService.delete(claim);
                    claimService.delete(claim);
                } catch (Exception rollbackFailure) {
                    plugin.getLogger().severe("Could not roll back incomplete claim " + claim.id() + ": "
                            + rollbackFailure.getMessage());
                }
            }
            if (exception instanceof SQLException) {
                plugin.getLogger().severe("Could not save claim for " + player.getName() + ": " + exception.getMessage());
                Messages.send(player, "The claim could not be saved. Nothing was changed.");
            } else {
                Messages.send(player, exception.getMessage());
            }
        }
    }

    public Map<UUID, ClaimSession> sessions() { return sessions; }
}
