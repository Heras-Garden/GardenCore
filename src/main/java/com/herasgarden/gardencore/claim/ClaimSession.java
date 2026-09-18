package com.herasgarden.gardencore.claim;

import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ClaimSession {
    private final UUID playerId;
    private final UUID worldId;
    private final ClaimType type;
    private final ClaimOwnerType ownerType;
    private final UUID ownerId;
    private final ClaimShape shape;
    private final List<ClaimPoint> points = new ArrayList<>();
    private UUID parentId;
    private boolean closed;
    private boolean fullHeight;
    private int minY;
    private int maxY;
    private int previewY;
    private boolean bottomSet;
    private boolean topSet;
    private ApartmentHeightStep apartmentHeightStep;
    private String claimName;
    private String territoryName;
    private ItemStack territoryFlag;

    public ClaimSession(UUID playerId, World world, ClaimType type, ClaimOwnerType ownerType,
                        UUID ownerId, ClaimShape shape, boolean fullHeight, int initialY) {
        this.playerId = playerId;
        this.worldId = world.getUID();
        this.type = type;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.shape = shape;
        this.fullHeight = fullHeight;
        this.previewY = initialY;
        if (type == ClaimType.APARTMENT) {
            this.apartmentHeightStep = ApartmentHeightStep.CEILING;
        } else {
            this.apartmentHeightStep = ApartmentHeightStep.DONE;
        }
        if (fullHeight) {
            this.minY = world.getMinHeight();
            this.maxY = world.getMaxHeight() - 1;
            this.bottomSet = true;
            this.topSet = true;
        } else {
            this.minY = initialY;
            this.maxY = initialY;
        }
    }

    public UUID playerId() { return playerId; }
    public UUID worldId() { return worldId; }
    public ClaimType type() { return type; }
    public ClaimOwnerType ownerType() { return ownerType; }
    public UUID ownerId() { return ownerId; }
    public ClaimShape shape() { return shape; }
    public UUID parentId() { return parentId; }
    public boolean closed() { return closed; }
    public boolean fullHeight() { return fullHeight; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int previewY() { return previewY; }
    public boolean bottomSet() { return bottomSet; }
    public boolean topSet() { return topSet; }
    public boolean heightConfigured() { return fullHeight || (bottomSet && topSet); }
    public ApartmentHeightStep apartmentHeightStep() { return apartmentHeightStep; }
    public boolean awaitingApartmentHeightClick() {
        return type == ClaimType.APARTMENT && closed && apartmentHeightStep != ApartmentHeightStep.DONE;
    }
    public String claimName() { return claimName; }
    public String territoryName() { return territoryName; }
    public ItemStack territoryFlag() { return territoryFlag == null ? null : territoryFlag.clone(); }
    public List<ClaimPoint> points() { return Collections.unmodifiableList(points); }

    public void setParentId(UUID parentId) { this.parentId = parentId; }
    public void setPreviewY(int previewY) { this.previewY = previewY; }
    public void setClaimName(String claimName) {
        this.claimName = claimName == null ? null : claimName.trim();
    }
    public void setTerritoryName(String territoryName) {
        this.territoryName = territoryName == null ? null : territoryName.trim();
    }
    public void setTerritoryFlag(ItemStack territoryFlag) {
        this.territoryFlag = territoryFlag == null ? null : territoryFlag.clone();
        if (this.territoryFlag != null) {
            this.territoryFlag.setAmount(1);
        }
    }

    public AddPointResult click(ClaimPoint point, int clickedY) {
        if (closed) {
            return AddPointResult.ALREADY_CLOSED;
        }

        parentId = null;
        if (points.isEmpty()) {
            points.add(point);
            previewY = clickedY;
            if (!fullHeight) {
                minY = clickedY;
                maxY = clickedY;
            }
            return AddPointResult.ADDED;
        }

        if (point.equals(points.get(0))) {
            int minimum = shape == ClaimShape.RECTANGLE ? 2 : 3;
            if (points.size() < minimum) {
                return AddPointResult.NEED_MORE_POINTS;
            }
            closed = true;
            if (type == ClaimType.APARTMENT && !heightConfigured()) {
                apartmentHeightStep = ApartmentHeightStep.CEILING;
                bottomSet = false;
                topSet = false;
            }
            return AddPointResult.CLOSED;
        }

        if (shape == ClaimShape.RECTANGLE && points.size() >= 2) {
            return AddPointResult.RECTANGLE_READY;
        }

        if (!points.contains(point)) {
            points.add(point);
            return AddPointResult.ADDED;
        }
        return AddPointResult.DUPLICATE;
    }

    public boolean undo() {
        if (points.isEmpty()) {
            return false;
        }
        if (closed) {
            closed = false;
            if (type == ClaimType.APARTMENT) {
                resetApartmentHeight();
            }
        }
        parentId = null;
        points.remove(points.size() - 1);
        return true;
    }

    public void reopen() {
        closed = false;
        parentId = null;
    }

    public ApartmentHeightResult selectApartmentHeight(int y) {
        if (type != ClaimType.APARTMENT || !closed) {
            return ApartmentHeightResult.NOT_READY;
        }
        if (apartmentHeightStep == ApartmentHeightStep.CEILING) {
            setTop(y);
            apartmentHeightStep = ApartmentHeightStep.FLOOR;
            return ApartmentHeightResult.CEILING_SET;
        }
        if (apartmentHeightStep == ApartmentHeightStep.FLOOR) {
            setBottom(y);
            apartmentHeightStep = ApartmentHeightStep.DONE;
            return ApartmentHeightResult.COMPLETE;
        }
        return ApartmentHeightResult.COMPLETE;
    }

    public void resetApartmentHeight() {
        if (type != ClaimType.APARTMENT) {
            return;
        }
        fullHeight = false;
        bottomSet = false;
        topSet = false;
        minY = previewY;
        maxY = previewY;
        apartmentHeightStep = ApartmentHeightStep.CEILING;
    }

    public void useFullHeight(World world) {
        fullHeight = true;
        minY = world.getMinHeight();
        maxY = world.getMaxHeight() - 1;
        bottomSet = true;
        topSet = true;
    }

    public void setBottom(int y) {
        fullHeight = false;
        minY = y;
        bottomSet = true;
        if (topSet && maxY < minY) {
            int other = maxY;
            maxY = minY;
            minY = other;
        }
    }

    public void setTop(int y) {
        fullHeight = false;
        maxY = y;
        topSet = true;
        if (bottomSet && minY > maxY) {
            int other = minY;
            minY = maxY;
            maxY = other;
        }
    }

    public ClaimGeometry geometry(World world) {
        if (points.size() < 2 || !world.getUID().equals(worldId)) {
            return null;
        }
        if (shape == ClaimShape.RECTANGLE) {
            return ClaimGeometry.rectangle(world, points.get(0), points.get(1), minY, maxY, fullHeight);
        }
        if (points.size() < 3) {
            return null;
        }
        return new ClaimGeometry(worldId, world.getName(), points, minY, maxY, fullHeight);
    }

    public enum ApartmentHeightStep {
        CEILING,
        FLOOR,
        DONE
    }

    public enum ApartmentHeightResult {
        CEILING_SET,
        COMPLETE,
        NOT_READY
    }

    public enum AddPointResult {
        ADDED,
        CLOSED,
        NEED_MORE_POINTS,
        RECTANGLE_READY,
        DUPLICATE,
        ALREADY_CLOSED
    }
}
