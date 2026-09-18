package com.herasgarden.gardencore.property;

import com.herasgarden.gardencore.GardenCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PropertyRegistry {
    private final GardenCore plugin;
    private final File file;
    private final Map<String, Property> properties = new LinkedHashMap<>();

    public PropertyRegistry(GardenCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "properties.yml");
    }

    public void load() {
        properties.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("properties");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String road = section.getString("road");
            String number = section.getString("number");
            String world = section.getString("world");
            String region = section.getString("region");
            if (road == null || number == null || world == null || region == null) {
                plugin.getLogger().warning("Skipping invalid property entry: " + key);
                continue;
            }

            Property property = new Property(road, number, world, region, section.getLong("price", 0L));
            property.setForSale(section.getBoolean("for-sale", true));

            String ownerUuid = section.getString("owner.uuid");
            String ownerName = section.getString("owner.name");
            if (ownerUuid != null && !ownerUuid.isBlank()) {
                try {
                    property.setOwner(UUID.fromString(ownerUuid), ownerName);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid owner UUID for property " + key + ".");
                }
            }

            property.setSignLocation(readLocation(section.getConfigurationSection("sign")));
            property.setMailboxLocation(readLocation(section.getConfigurationSection("mailbox")));
            properties.put(property.key(), property);
        }
    }

    public void save() throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Property property : properties.values()) {
            String base = "properties." + property.key();
            yaml.set(base + ".road", property.road());
            yaml.set(base + ".number", property.number());
            yaml.set(base + ".world", property.worldName());
            yaml.set(base + ".region", property.regionId());
            yaml.set(base + ".price", property.price());
            yaml.set(base + ".for-sale", property.forSale());
            yaml.set(base + ".owner.uuid", property.ownerUuid() == null ? null : property.ownerUuid().toString());
            yaml.set(base + ".owner.name", property.ownerName());
            writeLocation(yaml, base + ".sign", property.signLocation());
            writeLocation(yaml, base + ".mailbox", property.mailboxLocation());
        }
        yaml.save(file);
    }

    public boolean saveQuietly() {
        try {
            save();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save properties.yml: " + exception.getMessage());
            return false;
        }
    }

    public boolean add(Property property) {
        if (properties.containsKey(property.key())) {
            return false;
        }
        properties.put(property.key(), property);
        return true;
    }

    public Property get(String road, String number) {
        return properties.get(Property.key(road, number));
    }

    public Property getBySign(Location location) {
        if (location == null) {
            return null;
        }
        for (Property property : properties.values()) {
            if (sameBlock(property.signLocation(), location)) {
                return property;
            }
        }
        return null;
    }

    public Collection<Property> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(properties.values()));
    }

    public int size() {
        return properties.size();
    }

    private static boolean sameBlock(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private static Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }

    private static void writeLocation(YamlConfiguration yaml, String path, Location location) {
        if (location == null || location.getWorld() == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getBlockX());
        yaml.set(path + ".y", location.getBlockY());
        yaml.set(path + ".z", location.getBlockZ());
    }
}
