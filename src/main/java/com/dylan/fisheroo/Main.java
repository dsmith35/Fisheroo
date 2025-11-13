package com.dylan.fisheroo;

import java.util.Collections;
import org.bukkit.plugin.java.JavaPlugin;
import com.dylan.fisheroo.armorequip.ArmorListener;
import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class Main extends JavaPlugin {

    private static Economy econ = null;
    private boolean hasSkulls;

    private ItemManager itemManager;
    private PlayerManager playerManager;
    private LootManager lootManager;
    private RecipeManager recipeManager;
    private ArmorManager armorManager;
    private CommandManager commandManager;
    private BossManager bossManager;
    private ScoreboardManager scoreboardManager;
    private BiomeManager biomeManager;
    private SellManager sellManager;
    private WarpManager warpManager;
    private WeatherManager weatherManager;

    @Override
    public void onEnable() {
        getLogger().info("Fisheroo Plugin enabled!");
        saveDefaultConfig();

        // Check if Vault plugin is installed
        if (setupEconomy()) getLogger().info("Vault economy hooked successfully!");
        else getLogger().warning("Vault or economy plugin not found. Economy features will be disabled.");

        // Check if Skulls plugin is installed
        hasSkulls = Bukkit.getPluginManager().getPlugin("Skulls") != null;
        if (hasSkulls) getLogger().info("Skulls plugin detected!");
        else getLogger().info("Skulls plugin not found, skull items will be disabled.");

        // Initialize managers
        itemManager = new ItemManager(this, hasSkulls);
        playerManager = new PlayerManager(this);
        recipeManager = new RecipeManager(this);
        lootManager = new LootManager(this);
        armorManager = new ArmorManager(this);
        commandManager = new CommandManager(this);
        bossManager = new BossManager(this);
        scoreboardManager = new ScoreboardManager(this);
        biomeManager = new BiomeManager(this);
        sellManager = new SellManager(this);
        warpManager = new WarpManager(this);
        weatherManager =new WeatherManager(this);

        // Load configs and data
        Bukkit.getScheduler().runTask(this, () -> {
            itemManager.loadCustomItems(); // skulls will be ready by now
            lootManager.loadLootTables();
            recipeManager.loadRecipes();
            biomeManager.loadBiomes();
            bossManager.loadBosses();
        });
        

        // Register events
        getServer().getPluginManager().registerEvents(playerManager, this);
        getServer().getPluginManager().registerEvents(itemManager, this);
        getServer().getPluginManager().registerEvents(armorManager, this);
        getServer().getPluginManager().registerEvents(lootManager, this);
        getServer().getPluginManager().registerEvents(recipeManager, this);
        getServer().getPluginManager().registerEvents(sellManager, this);
        getServer().getPluginManager().registerEvents(warpManager, this);
        getServer().getPluginManager().registerEvents(new ArmorListener(Collections.emptyList()), this);

        // Register commands
        getCommand("customfish").setExecutor(commandManager.getCustomFishCommand());
        getCommand("setstat").setExecutor(commandManager.getSetStatCommand());
        getCommand("setstat").setTabCompleter(commandManager.getSetStatTabCompleter());
        getCommand("updateitems").setExecutor(commandManager.getUpdateItemsCommand());
        getCommand("giveitem").setExecutor(commandManager.getGiveItemCommand());
        getCommand("giveitem").setTabCompleter(commandManager.getGiveItemTabCompleter());
        getCommand("summonboss").setExecutor(commandManager.getSummonBossCommand());
        getCommand("summonboss").setTabCompleter(commandManager.getSummonBossTabCompleter());
        getCommand("odds").setExecutor(commandManager.getLootBreakdownCommand());
        getCommand("starter").setExecutor(commandManager.getStarterCommand());
        getCommand("discord").setExecutor(commandManager.getDiscordCommand());
        getCommand("fisheroo").setExecutor(commandManager.getFisherooCommand());
        getCommand("recipe").setExecutor(commandManager.getRecipeCommand());
        getCommand("recipe").setTabCompleter(commandManager.getRecipeTabCompleter());
        getCommand("recipes").setExecutor(commandManager.getAllRecipesCommand());
        getCommand("sell").setExecutor(commandManager.getSellCommand());
        getCommand("giveskull").setExecutor(commandManager.getGiveSkullCommand());
        getCommand("minbossrarity").setExecutor(commandManager.getMinBossRarityCommand());
        getCommand("minbossrarity").setTabCompleter(commandManager.getMinBossRarityTabCompleter());
        getCommand("customwarp").setExecutor(commandManager.getCustomWarpCommand());
        getCommand("customwarp").setTabCompleter(commandManager.getCustomWarpTabCompleter());
        getCommand("spawn").setExecutor(commandManager.getSpawnCommand());
        getCommand("beach").setExecutor(commandManager.getBeachCommand());
        getCommand("setglobalbonus").setExecutor(commandManager.getSetGlobalBonusCommand());
        getCommand("setglobalbonus").setTabCompleter(commandManager.getSetGlobalTabComplete());
        getCommand("setglobalmultiplier").setExecutor(commandManager.getSetGlobalMultiplierCommand());
        getCommand("setglobalmultiplier").setTabCompleter(commandManager.getSetGlobalTabComplete());
        getCommand("clearglobalmodifiers").setExecutor(commandManager.getClearGlobalModifiersCommand());
        getCommand("customweather").setExecutor(commandManager.getCustomWeatherCommand());

        // Register Other
        lootManager.startAFKTokenScheduler(60); // AFK Tokens
        weatherManager.startWeatherCycle();
    }

    @Override
    public void onDisable() {
        getLogger().info("Fisheroo Plugin disabled!");

        // Cancel schedulers
        getServer().getScheduler().cancelTasks(this);

        // Nullify managers to break references
        itemManager = null;
        playerManager = null;
        lootManager = null;
        recipeManager = null;
        armorManager = null;
        commandManager = null;
        bossManager = null;
        scoreboardManager = null;
        biomeManager = null;
        sellManager = null;
        warpManager = null;
        weatherManager = null;
    }

     // Vault economy setup
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    // Getters for managers
    public ItemManager getItemManager() { return itemManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public LootManager getLootManager() { return lootManager; }
    public RecipeManager getRecipeManager() { return recipeManager; }
    public ArmorManager getArmorManager() { return armorManager; }
    public CommandManager getCommandManager() { return commandManager; }
    public BossManager getBossManager() { return bossManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public BiomeManager getBiomeManager() { return biomeManager; }
    public SellManager getSellManager() { return sellManager; }
    public WarpManager getWarpManager() { return warpManager; }
    public WeatherManager getWeatherManager() { return weatherManager; }
}
