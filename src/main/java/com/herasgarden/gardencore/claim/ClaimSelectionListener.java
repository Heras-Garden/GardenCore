package com.herasgarden.gardencore.claim;

import com.herasgarden.gardencore.ui.ClaimChatUi;
import com.herasgarden.gardencore.util.Messages;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ClaimSelectionListener implements Listener {
    private final ClaimSessionManager sessions;

    public ClaimSelectionListener(ClaimSessionManager sessions) {
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        ClaimSession session = sessions.get(player);
        if (session == null) {
            return;
        }
        event.setCancelled(true);

        if (!player.getWorld().getUID().equals(session.worldId())) {
            Messages.send(player, "Return to the world where you started the claim.");
            return;
        }

        if (session.type() == ClaimType.UNIT && session.awaitingUnitHeightClick()) {
            ClaimSession.UnitHeightResult heightResult = session.selectUnitHeight(event.getClickedBlock().getY());
            if (heightResult == ClaimSession.UnitHeightResult.CEILING_SET) {
                Messages.send(player, "Ceiling set at Y " + event.getClickedBlock().getY() + ". Now right-click the floor.");
            } else if (heightResult == ClaimSession.UnitHeightResult.COMPLETE) {
                Messages.send(player, "Floor set at Y " + event.getClickedBlock().getY()
                        + ". Unit height is " + session.minY() + " to " + session.maxY() + ".");
            }
            sessions.showPreview(player);
            return;
        }

        ClaimPoint point = ClaimPoint.from(event.getClickedBlock().getLocation());
        ClaimSession.AddPointResult result = session.click(point, event.getClickedBlock().getY());
        switch (result) {
            case ADDED -> {
                Messages.send(player, "Point " + session.points().size() + " set at " + point.x() + ", " + point.z() + ".");
                if (session.shape() == ClaimShape.RECTANGLE && session.points().size() == 2) {
                    Messages.send(player, "Rectangle ready. Click the first point again to close it.");
                } else if (session.shape() == ClaimShape.POLYGON && session.points().size() >= 3) {
                    Messages.send(player, "Click the first point again when the boundary is finished.");
                }
            }
            case CLOSED -> {
                World world = player.getWorld();
                ClaimGeometry geometry = session.geometry(world);
                ClaimValidation validation = sessions.validation(player, session);
                ClaimChatUi.sendPreviewSummary(player, session, geometry, validation);
                ClaimChatUi.sendSelectionControls(player, session, validation);
                if (session.type() == ClaimType.UNIT) {
                    Messages.send(player, "Boundary closed. Right-click the ceiling, then right-click the floor.");
                    ClaimChatUi.sendUnitHeightPrompt(player, session);
                } else if (session.type() == ClaimType.TERRITORY) {
                    ClaimChatUi.sendTerritorySetup(player, session);
                }
            }
            case NEED_MORE_POINTS -> Messages.send(player,
                    session.shape() == ClaimShape.RECTANGLE ? "Select the opposite corner first."
                            : "A polygon needs at least three corners.");
            case RECTANGLE_READY -> Messages.send(player, "Click the first point again to close the rectangle, or use Undo.");
            case DUPLICATE -> Messages.send(player, "That corner is already part of the claim.");
            case ALREADY_CLOSED -> sessions.showPreview(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Drafts are intentionally temporary. Persistent draft requests will be introduced with government workflows.
        ClaimSession session = sessions.get(event.getPlayer());
        if (session != null) {
            sessions.sessions().remove(event.getPlayer().getUniqueId());
        }
    }
}
