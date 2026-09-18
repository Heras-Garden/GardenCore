package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.territory.TerritoryService;
import com.herasgarden.gardencore.ui.ClaimChatUi;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClaimSessionManager {
    private final GardenCore plugin;
    private final ClaimService claimService;
    private final TerritoryService territoryService;
    private final Map<UUID, ClaimSession> sessions = new ConcurrentHashMap<>();

    public ClaimSessionManager(GardenCore plugin, ClaimService claimService, TerritoryService territoryService) {
        this.plugin = plugin;
        this.claimService = claimService;
        this.territoryService = territoryService;
    }

    public ClaimSession start(Player player, ClaimType type) {
        return start(player, type, ClaimOwnerType.PLAYER, player.getUniqueId());
    }

    public ClaimSession start(Player player, ClaimType type, ClaimOwnerType ownerType, UUID ownerId) {
        ClaimShape shape = type.defaultsToPolygon() ? ClaimShape.POLYGON : ClaimShape.RECTANGLE;
        ClaimSession session = new ClaimSession(player.getUniqueId(), player.getWorld(), type,
                ownerType, ownerId, shape, type.defaultsToFullHeight(), player.getLocation().getBlockY());
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public ClaimSession get(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean has(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
        Messages.send(player, "Claim cancelled.");
    }

    public ClaimValidation validation(Player player, ClaimSession session) {
        World world = Bukkit.getWorld(session.worldId());
        if (world == null) {
            return ClaimValidation.invalid("The claim world is unavailable.");
        }
        ClaimGeometry geometry = session.geometry(world);
        if (geometry == null) {
            return ClaimValidation.invalid("Select more points first.");
        }
        if (session.type() == ClaimType.APARTMENT && !session.heightConfigured()) {
            return ClaimValidation.invalid("Right-click the apartment ceiling, then right-click the floor before confirming.");
        }
        if (session.type() == ClaimType.TERRITORY) {
            if (session.territoryName() == null || session.territoryName().isBlank()) {
                return ClaimValidation.invalid("Give the territory a name before confirming.");
            }
            if (session.territoryFlag() == null) {
                return ClaimValidation.invalid("Hold the territory banner and use Set Held Flag before confirming.");
            }
            if (!territoryService.isNameAvailable(session.territoryName())) {
                return ClaimValidation.invalid("That territory name is already in use.");
            }
        }
        if (session.parentId() == null) {
            ClaimType requiredParent = switch (session.type()) {
                case CITY -> ClaimType.TERRITORY;
                case DISTRICT -> ClaimType.CITY;
                case APARTMENT, HOTEL_ROOM -> ClaimType.BUILDING;
                default -> null;
            };
            Claim managedParent = requiredParent == null
                    ? claimService.findManagedParent(player, geometry)
                    : claimService.findManagedParent(player, geometry, requiredParent);
            if (managedParent != null) {
                session.setParentId(managedParent.id());
            }
        }

        if (session.type() == ClaimType.CITY || session.type() == ClaimType.DISTRICT) {
            String name = session.claimName();
            if (name == null || name.isBlank()) {
                return ClaimValidation.invalid("Give the " + session.type().name().toLowerCase()
                        + " a name with /claim name <name> before confirming.");
            }
            if (name.length() < 3 || name.length() > 48) {
                return ClaimValidation.invalid("City and district names must be between 3 and 48 characters.");
            }
            if (session.parentId() != null
                    && !claimService.isSiblingNameAvailable(session.parentId(), session.type(), name)) {
                return ClaimValidation.invalid("That " + session.type().name().toLowerCase()
                        + " name is already in use inside this parent.");
            }
        }

        return claimService.validate(geometry, session.type(), session.ownerType(), session.ownerId(), session.parentId());
    }

    public void showPreview(Player player) {
        ClaimSession session = get(player);
        if (session == null) {
            Messages.send(player, "You are not creating a claim.");
            return;
        }
        World world = Bukkit.getWorld(session.worldId());
        ClaimGeometry geometry = world == null ? null : session.geometry(world);
        ClaimValidation validation = geometry == null ? null : validation(player, session);
        ClaimChatUi.sendPreviewSummary(player, session, geometry, validation);
        ClaimChatUi.sendSelectionControls(player, session, validation);
        if (session.type() == ClaimType.APARTMENT && session.closed()) {
            ClaimChatUi.sendApartmentHeightPrompt(player, session);
        } else if (session.type() == ClaimType.TERRITORY) {
            ClaimChatUi.sendTerritorySetup(player, session);
        }
    }

    public void confirm(Player player) {
        ClaimSession session = get(player);
        if (session == null) {
            Messages.send(player, "You are not creating a claim.");
            return;
        }
        if (!session.closed()) {
            Messages.send(player, "Close the claim by clicking the first point again.");
            return;
        }
        World world = Bukkit.getWorld(session.worldId());
        if (world == null) {
            Messages.send(player, "The claim world is unavailable.");
            return;
        }
        ClaimGeometry geometry = session.geometry(world);
        if (geometry == null) {
            Messages.send(player, "The claim is incomplete.");
            return;
        }

        ClaimValidation validation = validation(player, session);
        if (!validation.valid()) {
            Messages.send(player, validation.reason());
            ClaimChatUi.sendSelectionControls(player, session, validation);
            if (session.type() == ClaimType.TERRITORY) {
                ClaimChatUi.sendTerritorySetup(player, session);
            }
            return;
        }

        Claim claim = null;
        try {
            String claimName = switch (session.type()) {
                case TERRITORY -> session.territoryName();
                case CITY, DISTRICT -> session.claimName();
                default -> null;
            };
            claim = claimService.create(session.type(), session.ownerType(), session.ownerId(),
                    session.parentId(), claimName, geometry, player.getUniqueId());

            if (session.type() == ClaimType.TERRITORY) {
                territoryService.register(claim, session.territoryName(), session.territoryFlag());
            }

            sessions.remove(player.getUniqueId());
            String created = switch (session.type()) {
                case TERRITORY -> "Territory " + session.territoryName() + " created.";
                case CITY -> "City " + session.claimName() + " created.";
                case DISTRICT -> "District " + session.claimName() + " created.";
                default -> "Claim created. ID: " + claim.id().toString().substring(0, 8) + ".";
            };
            Messages.send(player, created);
            if (session.type() == ClaimType.TERRITORY) {
                Messages.send(player, "Existing non-territory claims inside the boundary remain independent until their owners voluntarily transfer them.");
            }
            if (supportsPropertyListing(claim.type())) {
                if (claim.type() == ClaimType.APARTMENT) {
                    Messages.send(player, "Apartment setup needs two linked signs: place the room sign on the apartment wall/door area first, then place the matching mailbox sign on the front of its mailbox chest.");
                    Messages.send(player, "Use line 1 road, line 2 number and unit (example: 119 5A), line 3 price.");
                } else {
                    Messages.send(player, "Place the property sign on the front of its mailbox chest: line 1 road, line 2 number, line 3 price.");
                }
            }
        } catch (SQLException exception) {
            if (claim != null) {
                try {
                    claimService.delete(claim);
                } catch (Exception rollbackFailure) {
                    plugin.getLogger().severe("Could not roll back incomplete claim " + claim.id() + ": "
                            + rollbackFailure.getMessage());
                }
            }
            plugin.getLogger().severe("Could not save claim for " + player.getName() + ": " + exception.getMessage());
            Messages.send(player, "The claim could not be saved. Nothing was changed.");
        } catch (IllegalArgumentException exception) {
            if (claim != null) {
                try {
                    claimService.delete(claim);
                } catch (Exception rollbackFailure) {
                    plugin.getLogger().severe("Could not roll back incomplete claim " + claim.id() + ": "
                            + rollbackFailure.getMessage());
                }
            }
            Messages.send(player, exception.getMessage());
        }
    }

    private boolean supportsPropertyListing(ClaimType type) {
        return switch (type) {
            case PROPERTY, SHOP, FARM, BUILDING, APARTMENT, HOTEL_ROOM -> true;
            default -> false;
        };
    }

    public Map<UUID, ClaimSession> sessions() {
        return sessions;
    }
}
