package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.event.player.PlayerItemHeldEvent;

import com.dylan.fisheroo.fishing.LootEntry;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;


public class PlayerManager implements Listener {

    private final Main plugin;

    private final Map<UUID, Double> playerLuck = new HashMap<>();
    private final Map<UUID, Double> playerRuin = new HashMap<>();
    private final Map<UUID, Double> playerMultifish = new HashMap<>();
    private final Map<UUID, Biome> lastBiome = new HashMap<>();
    private final Map<UUID, Map<String, Double>> manualStats = new HashMap<>();
    private final Map<UUID, Set<Biome>> playerRestrictedBiomes = new HashMap<>();
    private final Map<UUID, FishHook> activeHooks = new HashMap<>();
    private final Map<UUID, Integer> minBossRarity = new HashMap<>();
    private final Map<String, Double> globalBonuses = new HashMap<>();
    private final Map<String, Double> globalMultipliers = new HashMap<>();
    
    public PlayerManager(Main plugin) {
        this.plugin = plugin;
    }


    // --- Player Luck ---
    public double getPlayerLuck(Player player) {
        return playerLuck.getOrDefault(player.getUniqueId(), plugin.getConfig().getDouble("luck_multiplier_default", 1.0));
    }
    public void setPlayerLuck(Player player, double luck) {
        playerLuck.put(player.getUniqueId(), luck);
    }

    // --- Player Ruin ---
    public double getPlayerRuin(Player player) {
        return playerRuin.getOrDefault(player.getUniqueId(), plugin.getConfig().getDouble("ruin_multiplier_default", 1.0));
    }
    public void setPlayerRuin(Player player, double ruin) {
        playerRuin.put(player.getUniqueId(), ruin);
    }


    // Player Multifish
    public double getPlayerMultifish(Player player) {
        return playerMultifish.getOrDefault(player.getUniqueId(), plugin.getConfig().getDouble("multifish_multiplier_default", 1.0));
    }
    public void setPlayerMultifish(Player player, double multifish) {
        playerMultifish.put(player.getUniqueId(), multifish);
    }


    // --- Events ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Only check if the player moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Biome currentBiome = player.getLocation().getBlock().getBiome();
        Biome previousBiome = lastBiome.get(player.getUniqueId());

        if (previousBiome == null || !previousBiome.equals(currentBiome)) {
            lastBiome.put(player.getUniqueId(), currentBiome);

            // Update scoreboard
            plugin.getScoreboardManager().updateScoreboard(player);

            // Build message
            Component biomeMessage = plugin.getBiomeManager().getCustomBiomeComponent(currentBiome.name());
            Component fullMessage = Component.text("§aYou have entered: ")
                    .append(biomeMessage)
                    .append(Component.text("\n§bTry /odds to see possible loot!"));

            // Send message
            player.sendMessage(fullMessage);
        }
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Biome biome = player.getLocation().getBlock().getBiome();
        lastBiome.put(player.getUniqueId(), biome);
        updateStats(player);
        plugin.getScoreboardManager().startUpdatingScoreboard(player, 100L);

        int updated = plugin.getItemManager().updatePlayerItems(player);
        if (updated > 0) {
            player.sendMessage(ChatColor.GREEN + "Updated " + updated + " custom item(s) to the latest version!");
        }

        // first join
        if (!player.hasPlayedBefore()) {
            ItemStack starterRod = plugin.getItemManager().getCustomItem("STARTER_ROD");
            if (starterRod != null) {
                player.getInventory().addItem(starterRod);
            }
            ItemStack starterSword = plugin.getItemManager().getCustomItem("STARTER_SWORD");
            if (starterSword != null) {
                player.getInventory().addItem(starterSword);
            }
            ItemStack fishingTokens = plugin.getItemManager().getCustomItem("FISHING_TOKEN");
            if (starterRod != null) {
                fishingTokens.setAmount(16);
                player.getInventory().addItem(fishingTokens);
            }

            plugin.getWarpManager().warpPlayer(player, "SPAWN"); // warp player to spawn
        }

        setMinBossRarity(player, 0); // reset min boss rarity filter

        plugin.getRecipeManager().resetRecipesForPlayer(player); // clears all recipes
        plugin.getRecipeManager().giveAllRecipesToPlayer(player); // give player all custom recipes
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Set respawn location to SPAWN
        Location spawnLocation = plugin.getWarpManager().getWarpLocation("SPAWN");
        if (spawnLocation != null) {
            event.setRespawnLocation(spawnLocation);
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        updateStats(player);
        FishHook hook = event.getHook();

        if (hook == null) return;

        // Cancel previous hook only when casting
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (activeHooks.containsKey(player.getUniqueId())) {
                FishHook oldHook = activeHooks.remove(player.getUniqueId());
                if (!oldHook.isDead()) oldHook.remove();
            }
            activeHooks.put(player.getUniqueId(), hook);
            
            applyCustomBiteTime(player, hook);
            return; // stop here, don't process fish yet
        }

        //CAUGHT FISH
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {

            //  aaaaa
            int multiFish = (int)getPlayerMultifish(player);
            for (int i = 0; i < multiFish; i++) {
                plugin.getLootManager().doCustomFish(player);
            }

            // Remove hook and unregister
            activeHooks.remove(player.getUniqueId());
            if (!hook.isDead()) hook.remove();

            // Spawn XP orb
            Location hookLoc = hook.getLocation();
            Location playerLoc = player.getLocation();
            org.bukkit.util.Vector direction = hookLoc.toVector().subtract(playerLoc.toVector()).normalize();
            Location spawnLoc = playerLoc.add(direction.multiply(1.5)).add(0, 1.0, 0);
            org.bukkit.entity.ExperienceOrb orb = player.getWorld().spawn(spawnLoc, org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(10);
            org.bukkit.util.Vector velocity = player.getEyeLocation().toVector().subtract(spawnLoc.toVector()).normalize().multiply(0.5);
            orb.setVelocity(velocity);

            event.setExpToDrop(0);
            event.setCancelled(true);
        }

        // --- MISSED CATCH ---
        if (event.getState() == PlayerFishEvent.State.FAILED_ATTEMPT
                || event.getState() == PlayerFishEvent.State.REEL_IN
                || event.getState() == PlayerFishEvent.State.IN_GROUND) {

            // Reapply same bite time so it doesn’t reset weirdly
            applyCustomBiteTime(player, hook);
        }
    }

    private void applyCustomBiteTime(Player player, FishHook hook) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        int fishingSpeedKey = plugin.getItemManager().getFishingSpeedFromItem(rod);
        String key = String.valueOf(fishingSpeedKey);

        int minTicks = plugin.getItemManager().getFishingSpeedsConfig().getInt("speeds." + key + ".min_sec", 3) * 20;
        int maxTicks = plugin.getItemManager().getFishingSpeedsConfig().getInt("speeds." + key + ".max_sec", 3) * 20;

        int ticksUntilBite = minTicks + (int) (Math.random() * (maxTicks - minTicks + 1));
        setBiteTime(hook, ticksUntilBite);
    }

    private void setBiteTime(FishHook hook, int ticks) {
        hook.setTimeUntilBite(ticks);
    }


    public Map<String, Double> getTotalStats(Player player) {
        Map<String, Double> stats = new HashMap<>();

        var config = plugin.getConfig();

        stats.put("luck", config.getDouble("luck_multiplier_default"));
        stats.put("ruin", config.getDouble("ruin_multiplier_default"));
        stats.put("multifish", config.getDouble("multifish_multiplier_default"));

        // Add armor stats
        Map<String, Double> armorStats = plugin.getArmorManager().getCombinedArmorStats(player);
        stats.put("luck", stats.get("luck") + armorStats.get("luck"));
        stats.put("ruin", stats.get("ruin") + armorStats.get("ruin"));
        stats.put("multifish", stats.get("multifish") + armorStats.get("multifish"));

        // Add main-hand item stats (only if fishing rod)
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == Material.FISHING_ROD) {
            double rodLuck = plugin.getItemManager().getLuckFromItem(mainHand);
            stats.put("luck", stats.get("luck") + rodLuck);
            double rodRuin = plugin.getItemManager().getRuinFromItem(mainHand);
            stats.put("ruin", stats.get("ruin") + rodRuin);
            double rodMultifish = plugin.getItemManager().getMultifishFromItem(mainHand);
            stats.put("multifish", stats.get("multifish") + rodMultifish);
        }

        // --- Artifact items in inventory ---
        FileConfiguration customItemsConfig = plugin.getItemManager().getCustomItemsConfig();
        Set<String> appliedArtifacts = new HashSet<>(); // track which artifact IDs have already applied

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");

            if (meta.getPersistentDataContainer().has(ITEM_ID_KEY, PersistentDataType.STRING)) {
                String id = meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);

                // Skip if we've already applied this artifact
                if (appliedArtifacts.contains(id)) continue;

                String yamlKey = plugin.getItemManager().getYamlKeyFromId(id);

                if (yamlKey != null && customItemsConfig.isConfigurationSection("items." + yamlKey)) {
                    String type = customItemsConfig.getString("items." + yamlKey + ".type", "");
                    if ("artifact".equalsIgnoreCase(type)) {
                        double artifactLuck = plugin.getItemManager().getLuckFromItem(item);
                        double artifactRuin = plugin.getItemManager().getRuinFromItem(item);
                        double artifactMultifish = plugin.getItemManager().getMultifishFromItem(item);

                        stats.put("luck", stats.get("luck") + artifactLuck);
                        stats.put("ruin", stats.get("ruin") + artifactRuin);
                        stats.put("multifish", stats.get("multifish") + artifactMultifish);

                        // Mark this artifact as applied
                        appliedArtifacts.add(id);
                    }
                }
            }
        }

        // --- Offhand Talisman ---
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.hasItemMeta()) {
            ItemMeta meta = offHand.getItemMeta();
            NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");

            if (meta.getPersistentDataContainer().has(ITEM_ID_KEY, PersistentDataType.STRING)) {
                String id = meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
                String yamlKey = plugin.getItemManager().getYamlKeyFromId(id);

                if (yamlKey != null && plugin.getItemManager().getCustomItemsConfig().isConfigurationSection("items." + yamlKey)) {
                    String type = plugin.getItemManager().getCustomItemsConfig().getString("items." + yamlKey + ".type", "");

                    if ("talisman".equalsIgnoreCase(type)) {
                        double talismanLuck = plugin.getItemManager().getLuckFromItem(offHand);
                        double talismanRuin = plugin.getItemManager().getRuinFromItem(offHand);
                        double talismanMultifish = plugin.getItemManager().getRuinFromItem(offHand);

                        stats.put("luck", stats.get("luck") + talismanLuck);
                        stats.put("ruin", stats.get("ruin") + talismanRuin);
                        stats.put("multifish", stats.get("multifish") + talismanMultifish);
                    }
                }
            }
        }

        // Apply global bonuses and multipliers
        stats.put("luck", (stats.get("luck") + getGlobalBonus("luck")) * getGlobalMultiplier("luck"));
        stats.put("ruin", (stats.get("ruin") + getGlobalBonus("ruin")) * getGlobalMultiplier("ruin"));
        stats.put("multifish", (stats.get("multifish") + getGlobalBonus("multifish")) * getGlobalMultiplier("multifish"));


        // Apply manual overrides if they exist
        if (manualStats.containsKey(player.getUniqueId())) {
            for (Map.Entry<String, Double> e : manualStats.get(player.getUniqueId()).entrySet()) {
                stats.put(e.getKey(), e.getValue());
            }
        }

        // Ensure stats don't go below 0
        stats.put("luck", Math.max(0, stats.get("luck")));
        stats.put("ruin", Math.max(0, stats.get("ruin")));
        stats.put("multifish", Math.max(0, stats.get("multifish")));

        return stats;
    }


    public void updateStats(Player player, Boolean updateImmediately) {
        Map<String, Double> stats = getTotalStats(player);

        setPlayerLuck(player, stats.get("luck"));
        setPlayerRuin(player, stats.get("ruin"));
        setPlayerMultifish(player, stats.get("multifish"));
        if (updateImmediately) {
            plugin.getScoreboardManager().updateScoreboard(player);
        }
        else {
            plugin.getScoreboardManager().scheduleScoreboardUpdate(player); // Schedule a throttled scoreboard update
        }
    }
    public void updateStats(Player player) {
        updateStats(player, false); // default to false
    }

    public void updateAllPlayerStats() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            updateStats(online, true);
        }
    }

    public double getGlobalBonus(String key) {
        return globalBonuses.getOrDefault(key, plugin.getConfig().getDouble("global_" + key + "_bonus", 0.0));
    }

    public double getGlobalMultiplier(String key) {
        return globalMultipliers.getOrDefault(key, plugin.getConfig().getDouble("global_" + key + "_multiplier", 1.0));
    }

    public void setGlobalBonus(String key, double value) {
        globalBonuses.put(key, value);
        updateAllPlayerStats();
    }

    public void setGlobalMultiplier(String key, double value) {
        globalMultipliers.put(key, value);
        updateAllPlayerStats();
    }

    public void clearGlobalModifiers() {
        var config = plugin.getConfig();
        globalBonuses.put("luck", config.getDouble("global_luck_bonus", 0.0));
        globalBonuses.put("ruin", config.getDouble("global_ruin_bonus", 0.0));
        globalBonuses.put("multifish", config.getDouble("global_multifish_bonus", 0.0));
        globalMultipliers.put("luck", config.getDouble("global_luck_multiplier", 1.0));
        globalMultipliers.put("ruin", config.getDouble("global_ruin_multiplier", 1.0));
        globalMultipliers.put("multifish", config.getDouble("global_multifish_multiplier", 1.0));
        updateAllPlayerStats();
    }


    // Called when player switches hotbar slots
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack item = inv.getItem(event.getNewSlot());

        // Cancel any active hook
        if (activeHooks.containsKey(player.getUniqueId())) {
            FishHook oldHook = activeHooks.remove(player.getUniqueId());
            if (!oldHook.isDead()) oldHook.remove();
        }


        // try to deal with illegal items from sell gui if they exist but they prob wont
        if (item != null && item.hasItemMeta()) {
            if (item.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "display_only"), PersistentDataType.BYTE)) {

                // Remove the item if display_only is true
                byte value = item.getItemMeta().getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "display_only"), PersistentDataType.BYTE);

                if (value == 1) {
                    inv.setItem(event.getNewSlot(), null);
                }
            }
        }

        // Delay the stats update by 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(player), 1L);
    }

    // Called when player clicks in inventory (including moving items around)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

       ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType() == Material.FISHING_ROD) {
            // Cancel hook only if a fishing rod is being moved/clicked
            FishHook oldHook = activeHooks.remove(player.getUniqueId());
            if (oldHook != null && !oldHook.isDead()) oldHook.remove();
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(player), 1L);
    }

    // Called when player picks up an item
    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(player), 1L);
    }

    // Called when player drops an item
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(player), 1L);
    }


    // Set a manual stat override
    public void setManualStat(Player player, String stat, double value) {
        manualStats.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(stat, value);
        updateStats(player, true);
    }

    // Clear a manual stat
    public void clearManualStat(Player player, String stat) {
        if (manualStats.containsKey(player.getUniqueId())) {
            manualStats.get(player.getUniqueId()).remove(stat);
            if (manualStats.get(player.getUniqueId()).isEmpty()) manualStats.remove(player.getUniqueId());
            updateStats(player, true);
        }
    }

    // Clear all manual stats (e.g., on logout)
    public void clearAllManualStats(Player player) {
        manualStats.remove(player.getUniqueId());
        updateStats(player, true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearAllManualStats(player);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        updateStats(player);

        FishHook oldHook = activeHooks.remove(player.getUniqueId());
        if (oldHook != null && !oldHook.isDead()) oldHook.remove();
    }

    public Map<String, Double> getManualStats(Player player) {
        return manualStats.getOrDefault(player.getUniqueId(), Collections.emptyMap());
    }

    public Biome getLastBiome(Player player) {
        return lastBiome.get(player.getUniqueId());
    }

    public int getMinBossRarity(Player player) {
        return minBossRarity.getOrDefault(player.getUniqueId(), 0);
    }

    public void setMinBossRarity(Player player, int rarity) {
        minBossRarity.put(player.getUniqueId(), rarity);
    }
}

