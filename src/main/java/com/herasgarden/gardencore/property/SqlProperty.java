package com.herasgarden.gardencore.property;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class SqlProperty {
    private final UUID id;
    private final UUID claimId;
    private final String scopeKey;
    private String road;
    private String number;
    private String unit;
    private long price;
    private boolean forSale;
    private String buyerAudience;
    private final Instant createdAt;

    public SqlProperty(UUID id, UUID claimId, String scopeKey, String road, String number, String unit,
                       long price, boolean forSale, String buyerAudience, Instant createdAt) {
        this.id = id;
        this.claimId = claimId;
        this.scopeKey = scopeKey == null || scopeKey.isBlank() ? "global" : scopeKey;
        this.road = road.trim();
        this.number = number.trim();
        this.unit = unit == null || unit.isBlank() ? null : unit.trim();
        this.price = Math.max(0L, price);
        this.forSale = forSale;
        this.buyerAudience = normalizeAudience(buyerAudience);
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID claimId() { return claimId; }
    public String scopeKey() { return scopeKey; }
    public String road() { return road; }
    public String number() { return number; }
    public String unit() { return unit; }
    public long price() { return price; }
    public boolean forSale() { return forSale; }
    public String buyerAudience() { return buyerAudience; }
    public Instant createdAt() { return createdAt; }

    public void setPrice(long price) { this.price = Math.max(0L, price); }
    public void setForSale(boolean forSale) { this.forSale = forSale; }
    public void setBuyerAudience(String buyerAudience) { this.buyerAudience = normalizeAudience(buyerAudience); }
    public void setAddress(String road, String number, String unit) {
        this.road = road.trim();
        this.number = number.trim();
        this.unit = unit == null || unit.isBlank() ? null : unit.trim();
    }

    public String address() {
        String base = number + " " + road;
        return unit == null ? base : base + ", " + unit;
    }

    public String addressKey() {
        return addressKey(scopeKey, road, number, unit);
    }

    public static String addressKey(String scopeKey, String road, String number, String unit) {
        return normalize(scopeKey == null ? "global" : scopeKey) + ":"
                + normalize(road) + ":" + normalize(number) + ":" + normalize(unit == null ? "" : unit);
    }

    public static String normalizeAudience(String value) {
        String normalized = value == null ? "ANY" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PLAYER", "SOCIETY" -> normalized;
            default -> "ANY";
        };
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }
}
