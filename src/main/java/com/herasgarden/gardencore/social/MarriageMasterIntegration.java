package com.herasgarden.gardencore.social;

import com.herasgarden.gardencore.api.social.MarriageDirectory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class MarriageMasterIntegration implements MarriageDirectory {
    private final JavaPlugin plugin;

    public MarriageMasterIntegration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void applyConfiguredSettings() {
        if (!plugin.getConfig().getBoolean("marriage.sync-marriage-master-settings", true)) return;

        Plugin marriageMaster = plugin.getServer().getPluginManager().getPlugin("MarriageMaster");
        if (marriageMaster == null || !marriageMaster.isEnabled()) {
            plugin.getLogger().info("MarriageMaster is not installed; Garden marriage sharing will stay inactive.");
            return;
        }

        try {
            Object configuration = marriageMaster.getClass().getMethod("getConfiguration").invoke(marriageMaster);
            set(configuration, "Marriage.RequirePriest", false);
            set(configuration, "Marriage.DivorceRequiresPriest", false);
            set(configuration, "Marriage.MaxPartners", 1);
            set(configuration, "Marriage.Confirmation.Enable", true);
            set(configuration, "Marriage.Confirmation.AutoDialog", true);
            set(configuration, "Economy.Enable", true);
            set(configuration, "Economy.Marry",
                    plugin.getConfig().getDouble("marriage.cost", 500.0D));
            set(configuration, "Economy.Divorce",
                    plugin.getConfig().getDouble("marriage.divorce-cost", 0.0D));

            Method save = findMethod(configuration.getClass(), "save", 0, null);
            if (save == null) throw new ReflectiveOperationException("MarriageMaster config save method was not found");
            save.invoke(configuration);

            Method reload = marriageMaster.getClass().getMethod("reload");
            reload.invoke(marriageMaster);

            plugin.getLogger().info("MarriageMaster configured for player proposals, confirmation, and Garden economy.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().warning("Could not apply Garden MarriageMaster settings: " + exception.getMessage());
        }
    }

    private void set(Object configuration, String key, Object value) throws ReflectiveOperationException {
        Method setter = findMethod(configuration.getClass(), "set", 2, value);
        if (setter == null) throw new ReflectiveOperationException("MarriageMaster config setter was not found");
        setter.invoke(configuration, key, value);
    }

    private Method findMethod(Class<?> type, String name, int parameterCount, Object secondValue) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) continue;
                if (parameterCount == 2) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params[0] != String.class || !compatible(params[1], secondValue)) continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean compatible(Class<?> target, Object value) {
        if (value == null) return !target.isPrimitive();
        if (!target.isPrimitive()) return target.isAssignableFrom(value.getClass()) || target == Object.class;
        return (target == boolean.class && value instanceof Boolean)
                || (target == int.class && value instanceof Integer)
                || (target == long.class && value instanceof Long)
                || (target == double.class && value instanceof Double)
                || (target == float.class && value instanceof Float);
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
