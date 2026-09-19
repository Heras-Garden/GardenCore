package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.GardenCore;
import com.herasgarden.gardencore.database.DatabaseManager;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class ClaimProfileService {
    private final GardenCore plugin;
    private final DatabaseManager database;
    private final ClaimService claims;

    public ClaimProfileService(GardenCore plugin, DatabaseManager database, ClaimService claims) {
        this.plugin = plugin;
        this.database = database;
        this.claims = claims;
    }

    public void ensure(UUID playerId) throws SQLException {
        long now = System.currentTimeMillis();
        long starting = Math.max(0L, plugin.getConfig().getLong("claims.home-blocks.starting", 50L));
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO gc_player_claim_profiles "
                             + "(player_uuid, earned_blocks, purchased_blocks, play_minutes, earning_locked, last_seen, updated_at) "
                             + "SELECT ?, ?, 0, 0, 0, ?, ? WHERE NOT EXISTS "
                             + "(SELECT 1 FROM gc_player_claim_profiles WHERE player_uuid = ?)")) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, starting);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, playerId.toString());
            statement.executeUpdate();
        }
    }

    public void touch(UUID playerId) throws SQLException {
        ensure(playerId);
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_claim_profiles SET last_seen = ?, updated_at = ? WHERE player_uuid = ?")) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setString(3, playerId.toString());
            statement.executeUpdate();
        }
    }

    public void recordPlayMinutes(UUID playerId, long minutes) throws SQLException {
        if (minutes <= 0) return;
        ensure(playerId);
        Profile before = profile(playerId);
        long newMinutes = before.playMinutes() + minutes;
        long earned = before.earnedBlocks();
        if (!before.earningLocked()) {
            long interval = Math.max(1L, plugin.getConfig().getLong("claims.home-blocks.minutes-per-award", 30L));
            long perAward = Math.max(1L, plugin.getConfig().getLong("claims.home-blocks.blocks-per-award", 1L));
            earned += Math.max(0L, newMinutes / interval - before.playMinutes() / interval) * perAward;
        }
        long now = System.currentTimeMillis();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_claim_profiles SET earned_blocks = ?, play_minutes = ?, last_seen = ?, updated_at = ? "
                             + "WHERE player_uuid = ?")) {
            statement.setLong(1, earned);
            statement.setLong(2, newMinutes);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.setString(5, playerId.toString());
            statement.executeUpdate();
        }
    }

    public long usedHomeBlocks(UUID playerId) { return claims.usedHomeBlocks(playerId); }

    public long totalHomeBlocks(UUID playerId) {
        try {
            ensure(playerId);
            Profile profile = profile(playerId);
            return Math.addExact(profile.earnedBlocks(), profile.purchasedBlocks());
        } catch (SQLException | ArithmeticException exception) {
            return Math.max(0L, plugin.getConfig().getLong("claims.home-blocks.starting", 50L));
        }
    }

    public long availableHomeBlocks(UUID playerId) {
        return Math.max(0L, totalHomeBlocks(playerId) - usedHomeBlocks(playerId));
    }

    public PurchaseResult purchase(Player player, long blocks) throws SQLException {
        if (blocks <= 0) throw new IllegalArgumentException("Claim blocks must be a positive whole number.");
        long pricePerBlock = Math.max(1L, plugin.getConfig().getLong("claims.home-blocks.purchase-price-per-block", 1L));
        long total;
        try {
            total = Math.multiplyExact(blocks, pricePerBlock);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("That claim-block purchase is too large.");
        }
        if (!plugin.currency().withdraw(player.getUniqueId(), total)) {
            return new PurchaseResult(false, "You need ⟡ " + total + " to buy " + blocks + " claim blocks.");
        }
        ensure(player.getUniqueId());
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_claim_profiles SET purchased_blocks = purchased_blocks + ?, updated_at = ? "
                             + "WHERE player_uuid = ?")) {
            statement.setLong(1, blocks);
            statement.setLong(2, System.currentTimeMillis());
            statement.setString(3, player.getUniqueId().toString());
            if (statement.executeUpdate() != 1) {
                plugin.currency().deposit(player.getUniqueId(), total);
                return new PurchaseResult(false, "The purchase could not be saved. Your Obols were returned.");
            }
        } catch (SQLException exception) {
            plugin.currency().deposit(player.getUniqueId(), total);
            throw exception;
        }
        return new PurchaseResult(true, "Bought " + blocks + " claim blocks for ⟡ " + total + ".");
    }

    public void lockEarning(UUID playerId) throws SQLException {
        ensure(playerId);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE gc_player_claim_profiles SET earning_locked = 1, updated_at = ? WHERE player_uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, playerId.toString());
            statement.executeUpdate();
        }
    }

    public int cleanupInactiveHomes() throws SQLException {
        int days = Math.max(1, plugin.getConfig().getInt("claims.inactivity.unprotected-home-days", 90));
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        int deleted = 0;
        for (Claim claim : claims.all().stream()
                .filter(value -> value.type() == ClaimType.HOME)
                .filter(value -> value.ownerType() == ClaimOwnerType.PLAYER).toList()) {
            if (claims.insideTerritory(claim)) continue;
            Profile profile = profileOrNull(claim.ownerId());
            if (profile == null || profile.lastSeen() > cutoff) continue;
            deleteAddressData(claim.id());
            claims.deleteInactiveHome(claim);
            deleted++;
        }
        return deleted;
    }

    private void deleteAddressData(UUID claimId) throws SQLException {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement signs = connection.prepareStatement(
                        "DELETE FROM gc_property_signs WHERE property_uuid IN "
                                + "(SELECT property_uuid FROM gc_properties WHERE claim_uuid = ?)")) {
                    signs.setString(1, claimId.toString());
                    signs.executeUpdate();
                }
                try (PreparedStatement mailboxes = connection.prepareStatement(
                        "DELETE FROM gc_property_mailboxes WHERE property_uuid IN "
                                + "(SELECT property_uuid FROM gc_properties WHERE claim_uuid = ?)")) {
                    mailboxes.setString(1, claimId.toString());
                    mailboxes.executeUpdate();
                }
                try (PreparedStatement properties = connection.prepareStatement(
                        "DELETE FROM gc_properties WHERE claim_uuid = ?")) {
                    properties.setString(1, claimId.toString());
                    properties.executeUpdate();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Profile profile(UUID playerId) throws SQLException {
        Profile profile = profileOrNull(playerId);
        if (profile == null) throw new SQLException("Claim profile is missing for " + playerId);
        return profile;
    }

    private Profile profileOrNull(UUID playerId) throws SQLException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT earned_blocks, purchased_blocks, play_minutes, earning_locked, last_seen "
                             + "FROM gc_player_claim_profiles WHERE player_uuid = ?")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                return new Profile(result.getLong("earned_blocks"), result.getLong("purchased_blocks"),
                        result.getLong("play_minutes"), result.getInt("earning_locked") != 0,
                        result.getLong("last_seen"));
            }
        }
    }

    public record PurchaseResult(boolean success, String message) {}
    private record Profile(long earnedBlocks, long purchasedBlocks, long playMinutes, boolean earningLocked, long lastSeen) {}
}
