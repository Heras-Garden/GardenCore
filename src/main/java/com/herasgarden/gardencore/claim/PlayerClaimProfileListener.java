package com.herasgarden.gardencore.claim;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;

public final class PlayerClaimProfileListener implements Listener {
    private final ClaimProfileService profiles;
    public PlayerClaimProfileListener(ClaimProfileService profiles) { this.profiles = profiles; }

    @EventHandler public void onJoin(PlayerJoinEvent event) { touch(event); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { touch(event); }

    private void touch(org.bukkit.event.player.PlayerEvent event) {
        try { profiles.touch(event.getPlayer().getUniqueId()); } catch (SQLException ignored) {}
    }
}
