package com.herasgarden.gardencore;

import com.herasgarden.gardencore.api.GardenPlatform;
import com.herasgarden.gardencore.api.claim.ClaimOwnershipBridge;
import com.herasgarden.gardencore.api.calendar.GardenCalendar;
import com.herasgarden.gardencore.api.claim.ClaimBlockService;
import com.herasgarden.gardencore.api.economy.GardenEconomy;
import com.herasgarden.gardencore.api.integration.IntegrationInbox;
import com.herasgarden.gardencore.api.integration.IntegrationOutbox;
import com.herasgarden.gardencore.api.order.OrderService;
import com.herasgarden.gardencore.api.organization.OrganizationDirectory;
import com.herasgarden.gardencore.api.land.PropertyManagementService;
import com.herasgarden.gardencore.api.storage.GardenStorage;
import com.herasgarden.gardencore.api.social.MarriageDirectory;
import com.herasgarden.gardencore.claim.*;
import com.herasgarden.gardencore.database.ClaimRepository;
import com.herasgarden.gardencore.database.DatabaseManager;
import com.herasgarden.gardencore.database.PropertyRepository;
import com.herasgarden.gardencore.economy.ObolCommand;
import com.herasgarden.gardencore.economy.ObolService;
import com.herasgarden.gardencore.ore.EmeraldOreService;
import com.herasgarden.gardencore.organization.CompanyCommand;
import com.herasgarden.gardencore.organization.OrganizationRepository;
import com.herasgarden.gardencore.organization.OrganizationService;
import com.herasgarden.gardencore.platform.PlatformSchema;
import com.herasgarden.gardencore.platform.claim.CoreClaimOwnershipBridge;
import com.herasgarden.gardencore.platform.calendar.SqlGardenCalendar;
import com.herasgarden.gardencore.calendar.CalendarCommand;
import com.herasgarden.gardencore.platform.economy.GardenBalanceService;
import com.herasgarden.gardencore.platform.economy.GardenVaultEconomyProvider;
import com.herasgarden.gardencore.platform.economy.VaultGardenEconomy;
import com.herasgarden.gardencore.platform.integration.SqlIntegrationInbox;
import com.herasgarden.gardencore.platform.integration.SqlIntegrationOutbox;
import com.herasgarden.gardencore.platform.land.CorePropertyManagementService;
import com.herasgarden.gardencore.platform.order.SqlOrderService;
import com.herasgarden.gardencore.platform.organization.CoreOrganizationDirectory;
import com.herasgarden.gardencore.platform.storage.CoreGardenStorage;
import com.herasgarden.gardencore.property.PropertyRegistry;
import com.herasgarden.gardencore.property.PropertySignListener;
import com.herasgarden.gardencore.property.PropertyService;
import com.herasgarden.gardencore.territory.TerritoryRepository;
import com.herasgarden.gardencore.territory.TerritoryService;
import com.herasgarden.gardencore.social.MarriageCommand;
import com.herasgarden.gardencore.social.MarriageService;
import com.herasgarden.gardencore.horse.HorseAccessListener;
import com.herasgarden.gardencore.horse.HorseCommand;
import com.herasgarden.gardencore.claim.AdminClaimCommand;
import com.herasgarden.gardencore.worldguard.WorldGuardHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class GardenCore extends JavaPlugin implements GardenPlatform {
    private Economy economy;
    private GardenBalanceService balanceService;
    private GardenVaultEconomyProvider gardenVaultEconomy;
    private ObolService obolService;

    // Transitional land implementation. These services stay live until
    // GardenLands takes over their commands and persistence completely.
    private PropertyRegistry legacyPropertyRegistry;
    private WorldGuardHook worldGuardHook;
    private EmeraldOreService emeraldOreService;

    private DatabaseManager databaseManager;
    private ClaimService claimService;
    private ClaimSessionManager claimSessionManager;
    private ClaimPreviewRenderer claimPreviewRenderer;
    private ClaimProfileService claimProfileService;
    private PropertyService propertyService;
    private OrganizationService organizationService;
    private TerritoryService territoryService;

    private OrderService orderService;
    private IntegrationOutbox integrationOutbox;
    private IntegrationInbox integrationInbox;
    private GardenStorage gardenStorage;
    private GardenEconomy gardenEconomy;
    private ClaimOwnershipBridge claimOwnershipBridge;
    private OrganizationDirectory organizationDirectory;
    private PropertyManagementService propertyManagementService;
    private MarriageService marriageService;
    private SqlGardenCalendar gardenCalendar;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Economy previousEconomy = currentEconomyProvider();
        if (!setupDatabase(previousEconomy)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getServicesManager().register(GardenPlatform.class, this, this, ServicePriority.Normal);
        getServer().getServicesManager().register(GardenStorage.class, gardenStorage, this, ServicePriority.Normal);
        getServer().getServicesManager().register(GardenEconomy.class, gardenEconomy, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ClaimOwnershipBridge.class, claimOwnershipBridge, this, ServicePriority.Normal);
        getServer().getServicesManager().register(ClaimBlockService.class, claimProfileService, this, ServicePriority.Normal);
        getServer().getServicesManager().register(OrganizationDirectory.class, organizationDirectory, this, ServicePriority.Normal);
        getServer().getServicesManager().register(
                PropertyManagementService.class, propertyManagementService, this, ServicePriority.Normal);
        try {
            gardenCalendar = new SqlGardenCalendar(this, gardenStorage);
        } catch (SQLException exception) {
            getLogger().severe("Garden calendar could not start: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getServicesManager().register(
                GardenCalendar.class, gardenCalendar, this, ServicePriority.Normal);
        PluginCommand calendar = getCommand("calendar");
        if (calendar != null) {
            CalendarCommand calendarCommand = new CalendarCommand(gardenCalendar);
            calendar.setExecutor(calendarCommand);
            calendar.setTabCompleter(calendarCommand);
        }
        marriageService = new MarriageService(this, databaseManager, gardenEconomy);
        try {
            marriageService.load();
        } catch (SQLException exception) {
            getLogger().severe("Garden marriage data could not be loaded: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        claimService.setMarriageDirectory(marriageService);
        getServer().getServicesManager().register(
                MarriageDirectory.class, marriageService, this, ServicePriority.Normal);

        PluginCommand marry = getCommand("marry");
        if (marry != null) {
            MarriageCommand marriageCommand = new MarriageCommand(this, marriageService);
            marry.setExecutor(marriageCommand);
            marry.setTabCompleter(marriageCommand);
        }

        obolService = new ObolService(this, economy);

        boolean legacyPropertySigns =
                getConfig().getBoolean("properties.legacy-worldguard-signs-enabled", false);
        if (legacyPropertySigns) {
            if (getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                worldGuardHook = new WorldGuardHook(this);
                legacyPropertyRegistry = new PropertyRegistry(this);
                legacyPropertyRegistry.load();
                getLogger().warning(
                        "Legacy WorldGuard/YAML property signs are ENABLED. "
                                + "Disable properties.legacy-worldguard-signs-enabled after migration.");
            } else {
                worldGuardHook = null;
                legacyPropertyRegistry = null;
                getLogger().warning(
                        "Legacy property signs were requested, but WorldGuard is not installed. "
                                + "The legacy listener will stay disabled.");
            }
        } else {
            worldGuardHook = null;
            legacyPropertyRegistry = null;
        }

        PluginCommand obolCommand = getCommand("obol");
        if (obolCommand != null) {
            ObolCommand obolExecutor = new ObolCommand(obolService);
            obolCommand.setExecutor(obolExecutor);
            obolCommand.setTabCompleter(obolExecutor);
        }

        claimPreviewRenderer = new ClaimPreviewRenderer(this, claimSessionManager);
        claimPreviewRenderer.start();

        ClaimCommand claimCommand = new ClaimCommand(
                claimService, claimSessionManager, organizationService, territoryService, propertyService,
                claimProfileService, claimPreviewRenderer);
        CompanyCommand companyCommand = new CompanyCommand(organizationService);
        GardenCommand gardenCommand = new GardenCommand(claimCommand, companyCommand);
        PluginCommand command = getCommand("garden");
        if (command != null) {
            command.setExecutor(gardenCommand);
            command.setTabCompleter(gardenCommand);
        }
        registerShortCommand("claim", gardenCommand, "claim");
        PluginCommand adminClaim = getCommand("adminclaim");
        if (adminClaim != null) {
            AdminClaimCommand adminExecutor = new AdminClaimCommand(gardenCommand);
            adminClaim.setExecutor(adminExecutor);
            adminClaim.setTabCompleter(adminExecutor);
        }
        // /property is owned by GardenLands. /garden property remains as a
        // temporary compatibility path while sign/storage code is extracted.
        registerShortCommand("company", gardenCommand, "company");

        if (worldGuardHook != null) {
            getServer().getPluginManager().registerEvents(
                    new PropertySignListener(this, legacyPropertyRegistry, worldGuardHook, economy), this);
        }
        getServer().getPluginManager().registerEvents(new ClaimSelectionListener(claimSessionManager), this);
        getServer().getPluginManager().registerEvents(new ClaimProtectionListener(claimService, claimSessionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerClaimProfileListener(claimProfileService), this);
        HorseAccessListener horseAccess = new HorseAccessListener(this);
        getServer().getPluginManager().registerEvents(horseAccess, this);
        PluginCommand horse = getCommand("horse");
        if (horse != null) {
            HorseCommand horseCommand = new HorseCommand(this, horseAccess);
            horse.setExecutor(horseCommand);
            horse.setTabCompleter(horseCommand);
        }

        long playtimeTicks = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(player -> {
            try {
                claimProfileService.recordPlayMinutes(player.getUniqueId(), 5L);
            } catch (SQLException exception) {
                getLogger().warning("Could not update claim playtime for " + player.getName() + ": " + exception.getMessage());
            }
        }), playtimeTicks, playtimeTicks);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                int removed = claimProfileService.cleanupInactiveHomes();
                if (removed > 0) getLogger().info("Released " + removed + " inactive wilderness HOME claim(s).");
            } catch (SQLException exception) {
                getLogger().warning("Inactive HOME cleanup failed: " + exception.getMessage());
            }
        }, 20L * 60L, 20L * 60L * 60L * 24L);

        long calendarTicks = Math.max(20L, getConfig().getLong("calendar.refresh-ticks", 20L));
        getServer().getScheduler().runTaskTimer(this, () -> {
            org.bukkit.World world = calendarWorld();
            if (world == null || gardenCalendar == null) return;
            gardenCalendar.updateFromWorld(world.getFullTime(), world.getTime());
            gardenCalendar.refreshHud(getServer().getOnlinePlayers());
        }, 1L, calendarTicks);

        emeraldOreService = new EmeraldOreService(this);
        emeraldOreService.start();

        getLogger().info("GardenCore platform enabled. Loaded " + claimService.size() + " transitional SQL claims, "
                + propertyService.all().size() + " SQL properties, and "
                + (legacyPropertyRegistry == null ? 0 : legacyPropertyRegistry.size())
                + " active legacy properties. Database: " + databaseManager.type() + ".");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (claimPreviewRenderer != null) {
            claimPreviewRenderer.stop();
        }
        if (legacyPropertyRegistry != null) {
            legacyPropertyRegistry.saveQuietly();
        }
        if (gardenCalendar != null) {
            gardenCalendar.hideAll();
        }
        if (emeraldOreService != null) {
            emeraldOreService.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("GardenCore disabled.");
    }

    public ClaimService claimService() {
        return claimService;
    }

    public PropertyService propertyService() {
        return propertyService;
    }

    public Economy economy() {
        return economy;
    }

    @Override
    public OrderService orders() {
        return orderService;
    }

    @Override
    public IntegrationOutbox integrations() {
        return integrationOutbox;
    }

    @Override
    public IntegrationInbox commands() {
        return integrationInbox;
    }

    @Override
    public GardenStorage storage() {
        return gardenStorage;
    }

    @Override
    public GardenEconomy currency() {
        return gardenEconomy;
    }

    private org.bukkit.World calendarWorld() {
        String configured = getConfig().getString("calendar.world", "").trim();
        if (!configured.isBlank()) {
            org.bukkit.World world = getServer().getWorld(configured);
            if (world != null) return world;
        }
        return getServer().getWorlds().isEmpty() ? null : getServer().getWorlds().getFirst();
    }

    private void registerShortCommand(String name, GardenCommand gardenCommand, String root) {
        PluginCommand shortCommand = getCommand(name);
        if (shortCommand == null) {
            getLogger().warning("Command /" + name + " is missing from plugin.yml.");
            return;
        }
        SubcommandAliasCommand adapter = new SubcommandAliasCommand(gardenCommand, root);
        shortCommand.setExecutor(adapter);
        shortCommand.setTabCompleter(adapter);
    }

    private boolean setupDatabase(Economy previousEconomy) {
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            PlatformSchema.ensure(databaseManager);
            gardenStorage = new CoreGardenStorage(databaseManager);

            balanceService = new GardenBalanceService(
                    databaseManager,
                    getConfig().getLong("obols.starting-balance", 0L)
            );
            migrateExistingEconomy(previousEconomy);
            gardenVaultEconomy = new GardenVaultEconomyProvider(
                    balanceService,
                    getConfig().getString("obols.singular", "Obol"),
                    getConfig().getString("obols.plural", "Obols")
            );
            getServer().getServicesManager().register(
                    Economy.class, gardenVaultEconomy, this, ServicePriority.Highest);
            economy = gardenVaultEconomy;
            gardenEconomy = new VaultGardenEconomy(
                    economy,
                    getConfig().getString("obols.symbol", "⟡")
            );
            orderService = new SqlOrderService(databaseManager);
            integrationOutbox = new SqlIntegrationOutbox(databaseManager);
            integrationInbox = new SqlIntegrationInbox(databaseManager);

            OrganizationRepository organizationRepository = new OrganizationRepository(databaseManager);
            organizationService = new OrganizationService(organizationRepository);
            organizationService.load();
            organizationDirectory = new CoreOrganizationDirectory(organizationService, organizationRepository);

            ClaimRepository claimRepository = new ClaimRepository(databaseManager);
            claimService = new ClaimService(this, claimRepository, organizationService);
            claimService.load();
            claimProfileService = new ClaimProfileService(this, databaseManager, claimService);
            claimService.setProfiles(claimProfileService);
            claimOwnershipBridge = new CoreClaimOwnershipBridge(claimService);

            TerritoryRepository territoryRepository = new TerritoryRepository(databaseManager);
            territoryService = new TerritoryService(territoryRepository);
            territoryService.load();
            claimSessionManager = new ClaimSessionManager(this, claimService, territoryService, claimProfileService);

            PropertyRepository propertyRepository = new PropertyRepository(databaseManager);
            propertyService = new PropertyService(this, claimService, propertyRepository, economy, organizationService);
            propertyService.load();
            propertyManagementService = new CorePropertyManagementService(propertyService, claimService);
            return true;
        } catch (SQLException | RuntimeException exception) {
            getLogger().severe("GardenCore database could not start: " + exception.getMessage());
            exception.printStackTrace();
            if (databaseManager != null) {
                databaseManager.close();
            }
            return false;
        }
    }

    private Economy currentEconomyProvider() {
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    private void migrateExistingEconomy(Economy previousEconomy) throws SQLException {
        if (!getConfig().getBoolean("obols.migrate-existing-vault-provider", true)
                || previousEconomy == null
                || previousEconomy instanceof GardenVaultEconomyProvider
                || balanceService.migrationComplete()) {
            return;
        }

        int imported = 0;
        for (org.bukkit.OfflinePlayer player : org.bukkit.Bukkit.getOfflinePlayers()) {
            if (!previousEconomy.hasAccount(player)) {
                continue;
            }
            long amount = Math.max(0L, (long) Math.floor(previousEconomy.getBalance(player)));
            balanceService.set(player.getUniqueId(), amount);
            imported++;
        }
        balanceService.markMigrationComplete(previousEconomy.getName());
        getLogger().info("Imported " + imported + " player balance(s) from "
                + previousEconomy.getName() + " into GardenCore.");
    }
}
