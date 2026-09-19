package com.herasgarden.gardencore.horse;

import com.herasgarden.gardencore.api.social.MarriageDirectory;
import com.herasgarden.gardencore.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class HorseAccessListener implements Listener {
    private final NamespacedKey publicKey;
    private final JavaPlugin plugin;
    private final NamespacedKey trustedKey;
    private final NamespacedKey nameRequiredKey;

    public HorseAccessListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.publicKey = new NamespacedKey(plugin, "horse-public");
        this.trustedKey = new NamespacedKey(plugin, "horse-trusted");
        this.nameRequiredKey = new NamespacedKey(plugin, "horse-name-required");
    }

    public boolean owns(Player player, AbstractHorse horse) {
        AnimalTamer owner = horse.getOwner();
        return owner != null && owner.getUniqueId().equals(player.getUniqueId());
    }

    public boolean publicAccess(AbstractHorse horse) {
        Byte value = horse.getPersistentDataContainer().get(publicKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public void setPublic(AbstractHorse horse, boolean value) {
        horse.getPersistentDataContainer().set(publicKey, PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }

    public Set<UUID> trusted(AbstractHorse horse) {
        String raw = horse.getPersistentDataContainer().get(trustedKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> {
                    try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setTrusted(AbstractHorse horse, Set<UUID> trusted) {
        String raw = trusted.stream().map(UUID::toString).collect(Collectors.joining(","));
        horse.getPersistentDataContainer().set(trustedKey, PersistentDataType.STRING, raw);
    }

    public boolean requiresName(AbstractHorse horse) {
        Byte value = horse.getPersistentDataContainer().get(nameRequiredKey, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    public void nameHorse(AbstractHorse horse, String name) {
        horse.customName(Component.text(name));
        horse.setCustomNameVisible(true);
        horse.getPersistentDataContainer().set(nameRequiredKey, PersistentDataType.BYTE, (byte) 0);
    }

    public boolean spouseAccess(Player player, AbstractHorse horse) {
        AnimalTamer owner = horse.getOwner();
        if (owner == null) return false;
        RegisteredServiceProvider<MarriageDirectory> registration =
                plugin.getServer().getServicesManager().getRegistration(MarriageDirectory.class);
        return registration != null
                && registration.getProvider() != null
                && registration.getProvider().arePartners(player.getUniqueId(), owner.getUniqueId());
    }

    public boolean canRide(Player player, AbstractHorse horse) {
        return owns(player, horse)
                || spouseAccess(player, horse)
                || publicAccess(horse)
                || trusted(horse).contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse) || !(event.getOwner() instanceof Player player)) return;
        setPublic(horse, false);
        if (horse.customName() == null) {
            horse.getPersistentDataContainer().set(nameRequiredKey, PersistentDataType.BYTE, (byte) 1);
            Messages.send(player, "You tamed a horse. Name it with /horse name <name> before riding it.");
        } else {
            horse.setCustomNameVisible(true);
            horse.getPersistentDataContainer().set(nameRequiredKey, PersistentDataType.BYTE, (byte) 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;
        if (!horse.isTamed() || horse.getOwner() == null) return;
        if (requiresName(horse)) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), owns(event.getPlayer(), horse)
                    ? "Name your horse first with /horse name <name>."
                    : "This horse must be named by its owner before it can be ridden.");
            return;
        }
        if (!canRide(event.getPlayer(), horse)) {
            event.setCancelled(true);
            Messages.send(event.getPlayer(), "That horse is private.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || !(event.getMount() instanceof AbstractHorse horse)) return;
        if (!horse.isTamed() || horse.getOwner() == null) return;
        if (requiresName(horse)) {
            event.setCancelled(true);
            Messages.send(player, owns(player, horse)
                    ? "Name your horse first with /horse name <name>."
                    : "This horse must be named by its owner before it can be ridden.");
            return;
        }
        if (!canRide(player, horse)) {
            event.setCancelled(true);
            Messages.send(player, "That horse is private.");
        }
    }
}
