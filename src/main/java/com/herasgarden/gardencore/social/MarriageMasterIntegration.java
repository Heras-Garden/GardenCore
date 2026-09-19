package com.herasgarden.gardencore.social;

import com.herasgarden.gardencore.api.social.MarriageDirectory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class MarriageMasterIntegration implements MarriageDirectory {
    private final JavaPlugin plugin;

    public MarriageMasterIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Set<UUID> partners(UUID playerId) {
        Plugin marriageMaster = plugin.getServer().getPluginManager().getPlugin("MarriageMaster");
        if (marriageMaster == null || !marriageMaster.isEnabled() || playerId == null) return Set.of();
        try {
            Method getPlayerData = marriageMaster.getClass().getMethod("getPlayerData", UUID.class);
            Object data = getPlayerData.invoke(marriageMaster, playerId);
            if (data == null) return Set.of();
            Method getPartners = data.getClass().getMethod("getPartners");
            Object raw = getPartners.invoke(data);
            if (!(raw instanceof Collection<?> collection)) return Set.of();
            Set<UUID> result = new LinkedHashSet<>();
            for (Object partner : collection) {
                if (partner == null) continue;
                Method getUuid = partner.getClass().getMethod("getUUID");
                Object uuid = getUuid.invoke(partner);
                if (uuid instanceof UUID value) result.add(value);
            }
            return Set.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("MarriageMaster integration lookup failed: " + exception.getMessage());
            return Set.of();
        }
    }
}
