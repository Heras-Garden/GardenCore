package com.herasgarden.gardencore.property;

import org.bukkit.Location;

import java.util.Locale;
import java.util.UUID;

public final class Property {
    private final String key;
    private final String road;
    private final String number;
    private final String worldName;
    private final String regionId;
    private UUID ownerUuid;
    private String ownerName;
    private long price;
    private boolean forSale;
    private Location signLocation;
    private Location mailboxLocation;

    public Property(String road, String number, String worldName, String regionId, long price) {
        this.key = key(road, number);
        this.road = road.trim();
        this.number = number.trim();
        this.worldName = worldName;
        this.regionId = regionId;
        this.price = price;
        this.forSale = true;
    }

    public static String key(String road, String number) {
        return normalize(road) + ":" + normalize(number);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    public String key() { return key; }
    public String road() { return road; }
    public String number() { return number; }
    public String worldName() { return worldName; }
    public String regionId() { return regionId; }
    public UUID ownerUuid() { return ownerUuid; }
    public String ownerName() { return ownerName; }
    public long price() { return price; }
    public boolean forSale() { return forSale; }
    public Location signLocation() { return signLocation; }
    public Location mailboxLocation() { return mailboxLocation; }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public void clearOwner() {
        this.ownerUuid = null;
        this.ownerName = null;
    }

    public void setPrice(long price) { this.price = price; }
    public void setForSale(boolean forSale) { this.forSale = forSale; }
    public void setSignLocation(Location signLocation) { this.signLocation = blockLocation(signLocation); }
    public void setMailboxLocation(Location mailboxLocation) { this.mailboxLocation = blockLocation(mailboxLocation); }

    private static Location blockLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
