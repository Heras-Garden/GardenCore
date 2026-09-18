package com.herasgarden.gardencore.territory;

import com.herasgarden.gardencore.claim.Claim;
import com.herasgarden.gardencore.claim.ClaimType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryService {
    private final TerritoryRepository repository;
    private final Set<String> nameKeys = ConcurrentHashMap.newKeySet();

    public TerritoryService(TerritoryRepository repository) {
        this.repository = repository;
    }

    public void load() throws SQLException {
        nameKeys.clear();
        nameKeys.addAll(repository.loadNameKeys());
    }

    public boolean isNameAvailable(String name) {
        return name != null && !name.isBlank() && !nameKeys.contains(normalize(name));
    }

    public void register(Claim claim, String name, ItemStack flag) throws SQLException {
        if (claim == null || claim.type() != ClaimType.TERRITORY) {
            throw new IllegalArgumentException("Only territory claims can have territory metadata.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A territory name is required.");
        }
        if (flag == null || !isBanner(flag)) {
            throw new IllegalArgumentException("A banner flag is required.");
        }
        String key = normalize(name);
        if (!nameKeys.add(key)) {
            throw new IllegalArgumentException("That territory name is already in use.");
        }
        try {
            repository.insert(claim.id(), name.trim(), key, serializeFlag(flag));
        } catch (SQLException exception) {
            nameKeys.remove(key);
            throw exception;
        }
    }

    public void delete(Claim claim) throws SQLException {
        if (claim != null && claim.type() == ClaimType.TERRITORY) {
            repository.delete(claim.id());
            if (claim.name() != null) {
                nameKeys.remove(normalize(claim.name()));
            }
        }
    }

    public boolean isBanner(ItemStack item) {
        return item != null && item.getType().name().endsWith("_BANNER");
    }

    private String serializeFlag(ItemStack flag) {
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack copy = flag.clone();
        copy.setAmount(1);
        yaml.set("flag", copy);
        return yaml.saveToString();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }
}
