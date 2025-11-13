package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.dylan.fisheroo.fishing.LootEntry;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.dylan.fisheroo.ItemManager.RarityInfo;
import org.bukkit.entity.Entity;

public class LootManager implements Listener {

    private final Main plugin;
    private Map<String, Map<String, Double>> treasureLoot = new HashMap<>();
    private Map<String, Map<String, Double>> junkLoot = new HashMap<>();
    private Map<String, Map<String, Double>> bossOdds = new HashMap<>();
    private FileConfiguration lootTablesConfig;
    private File lootTablesFile;
    private final Map<UUID, Long> lastTokenTime = new HashMap<>();

    public LootManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadLootTables() {
        treasureLoot.clear();
        junkLoot.clear();
        bossOdds.clear();

        // Load loot_tables.yml
        lootTablesFile = new File(plugin.getDataFolder(), "loot_tables.yml");
        if (!lootTablesFile.exists()) plugin.saveResource("loot_tables.yml", false);
        lootTablesConfig = YamlConfiguration.loadConfiguration(lootTablesFile);

        for (String biomeKey : lootTablesConfig.getConfigurationSection("biomes").getKeys(false)) {
            Map<String, Double> treasures = new LinkedHashMap<>();
            if (lootTablesConfig.contains("biomes." + biomeKey + ".treasure")) {
                for (String key : lootTablesConfig.getConfigurationSection("biomes." + biomeKey + ".treasure").getKeys(false)) {
                    treasures.put(key, lootTablesConfig.getDouble("biomes." + biomeKey + ".treasure." + key));
                }
            }
            Map<String, Double> junks = new LinkedHashMap<>();
            if (lootTablesConfig.contains("biomes." + biomeKey + ".junk")) {
                for (String key : lootTablesConfig.getConfigurationSection("biomes." + biomeKey + ".junk").getKeys(false)) {
                    junks.put(key, lootTablesConfig.getDouble("biomes." + biomeKey + ".junk." + key));
                }
            }

            Map<String, Double> bosses = new LinkedHashMap<>();
            if (lootTablesConfig.contains("biomes." + biomeKey + ".bosses")) {
                for (String key : lootTablesConfig.getConfigurationSection("biomes." + biomeKey + ".bosses").getKeys(false)) {
                    bosses.put(key, lootTablesConfig.getDouble("biomes." + biomeKey + ".bosses." + key));
                }
            }

            treasureLoot.put(biomeKey, treasures);
            junkLoot.put(biomeKey, junks);
            bossOdds.put(biomeKey, bosses);
        }
    }

    public void doCustomFish(Player player) {
        plugin.getPlayerManager().updateStats(player); //update stats

        Biome biome = player.getLocation().getBlock().getBiome();
        String biomeKey = biome.name();
        boolean gotLoot = false;
        double luck = plugin.getPlayerManager().getPlayerLuck(player);
        

        // 1. Build loot by rarity
        Map<Integer, List<LootEntry>> lootByRarity = new TreeMap<>(Collections.reverseOrder()); // high rarity first

        Map<String, Double> treasures = treasureLoot.getOrDefault(biomeKey, new LinkedHashMap<>());
        for (String itemId : treasures.keySet()) {
            ItemStack item = plugin.getItemManager().getCustomItem(itemId);
            if (item == null) continue;
            int rarity = plugin.getItemManager().getCustomItemsConfig().getInt("items." + itemId + ".rarity", 0);
            lootByRarity.computeIfAbsent(rarity, k -> new ArrayList<>())
                .add(new LootEntry(itemId, rarity, treasures.get(itemId)));
        }

        // 2. Compute final chances by tier
        double remaining = 1.0;
        for (int rarity : lootByRarity.keySet()) {
            List<LootEntry> tier = lootByRarity.get(rarity);
            double tierBaseTotal = tier.stream().mapToDouble(e -> e.baseChance).sum();
            double tierScaled = Math.min(tierBaseTotal * luck, remaining);
            remaining -= tierScaled;

            for (LootEntry entry : tier) {
                entry.finalChance = tierScaled * (entry.baseChance / tierBaseTotal);
            }
        }
        

        // 3. Roll the loot
        double roll = Math.random();
        double cumulative = 0;
        for (int rarity : lootByRarity.keySet()) {
            if (gotLoot) { break; }
            for (LootEntry entry : lootByRarity.get(rarity)) {
                cumulative += entry.finalChance;
                if (roll <= cumulative) {
                    ItemStack item = plugin.getItemManager().getCustomItem(entry.itemId);
                    if (item != null) {
                        player.getInventory().addItem(item);
                        player.sendMessage(ChatColor.GREEN + "You fished: " + item.getItemMeta().getDisplayName());
                        // broadcast
                        int itemRarity = plugin.getItemManager().getCustomItemsConfig()
                            .getInt("items." + entry.itemId + ".rarity", 0);
                        double itemOdds = getItemOddsForBiome(entry.itemId, biome.name());
                        broadcastRareCatch(player, item, itemRarity, itemOdds);
                        handleLootSounds(player, itemRarity);
                    }
                    gotLoot = true;
                    break;
                }
            }
        }

        // fallback to junk
        Map<String, Double> junks = junkLoot.getOrDefault(biomeKey, new LinkedHashMap<>());
        if (!gotLoot && !junks.isEmpty()) {
            double total = junks.values().stream().mapToDouble(d -> d).sum();
            double r = Math.random() * total;
            double cum = 0;
            for (Map.Entry<String, Double> entry : junks.entrySet()) {
                cum += entry.getValue();
                if (r <= cum) {
                    ItemStack item = plugin.getItemManager().getCustomItem(entry.getKey());
                    if (item != null) player.getInventory().addItem(item);
                    gotLoot = true;
                    break;
                }
            }
        }

        if (!gotLoot) {
            player.sendMessage("You caught nothing!");
        }

        // Try to summon a boss
        trySpawnBoss(player);
    }


    public double getItemOddsForBiome(String itemId, String biomeKey) {
        Map<String, Double> baseBreakdown = getDefaultBiomeLoot(biomeKey);
        if (baseBreakdown == null || baseBreakdown.isEmpty()) {
            return 0.0;
        }
        return baseBreakdown.getOrDefault(itemId, 0.0);
    }

    public void handleLootSounds(Player player, int rarity) {
        // --- Personal sound (only for specific rarities) ---
        if (rarity >= 4) {
            Sound personalSound;
            float personalPitch = 1.0f;

            switch (rarity) {
                case 4: personalSound = Sound.BLOCK_NOTE_BLOCK_BELL; break;
                case 5: personalSound = Sound.BLOCK_NOTE_BLOCK_BELL; break;
                case 6: personalSound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH; personalPitch = 1.1f; break;
                case 7: personalSound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH; personalPitch = 1.2f; break;
                case 8: personalSound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH; personalPitch = 1.3f; break;
                default: return;
            }

            player.playSound(player.getLocation(), personalSound, 1.0f, personalPitch);
        }

        // --- Global sound (only for certain rarities, independent) ---
        if (rarity >= 6) { 
            Sound globalSound;
            float globalPitch = 1.0f;

            switch (rarity) {
                case 6: globalSound = Sound.BLOCK_ENCHANTMENT_TABLE_USE; globalPitch = 1.2f; break;
                case 7: globalSound = Sound.ENTITY_WITHER_SPAWN; globalPitch = 1.2f; break;
                case 8: globalSound = Sound.ENTITY_ENDER_DRAGON_GROWL; globalPitch = 1.3f; break;
                default: return;
            }
            // Play to all players in the world
            for (Player p : player.getWorld().getPlayers()) {
                p.playSound(player.getLocation(), globalSound, 1.0f, globalPitch);
            }
        }
    }




    @EventHandler
    public void onUseFishingToken(PlayerInteractEvent event) {
        Action action = event.getAction();

        // Allow right and left clicks, both air and block
        if (action != Action.RIGHT_CLICK_AIR 
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        ItemStack fishingToken = plugin.getItemManager().getCustomItem("FISHING_TOKEN");

        if (heldItem != null && heldItem.isSimilar(fishingToken)) {
            int amountToUse = 1; // default: 1 token

            // If sneaking, use up to 10 tokens at once
            if (player.isSneaking()) {
                amountToUse = Math.min(10, heldItem.getAmount());
            }

            // Consume the tokens
            int remaining = heldItem.getAmount() - amountToUse;
            if (remaining > 0) {
                heldItem.setAmount(remaining);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            // Run your custom fishing logic for each token used
            for (int i = 0; i < amountToUse; i++) {
                doCustomFish(player);
            }

            player.sendActionBar(ChatColor.GREEN + "You used " + amountToUse + " fishing token" + (amountToUse > 1 ? "s" : "") + "!");
        }
    }


    public void startAFKTokenScheduler(int freq) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                // Check if player is in the specific biome
                if (player.getLocation().getBlock().getBiome() == Biome.WINDSWEPT_GRAVELLY_HILLS) {
                    UUID playerId = player.getUniqueId();
                    long lastTime = lastTokenTime.getOrDefault(playerId, 0L);

                    // Only give token if 5 minutes have passed
                    if (now - lastTime >= freq * 1000) { // 2 minutes in milliseconds
                        ItemStack fishingToken = plugin.getItemManager().getCustomItem("FISHING_TOKEN");
                        if (fishingToken != null) {
                            player.getInventory().addItem(fishingToken);
                            player.sendActionBar(ChatColor.AQUA + "You received a fishing token for being in the AFK Zone!");
                            lastTokenTime.put(playerId, now); // Update last given time
                        }
                    }
                }
            }
        }, 0L, 20L * 60); // run every 60 seconds (check often, won't give multiple due to map)
    }

    public Map<String, Double> getBiomeLootWithLuck(String biome, double playerLuck) {
        Map<String, Double> finalChances = new LinkedHashMap<>();

        if (!treasureLoot.containsKey(biome) && !junkLoot.containsKey(biome)) return finalChances;

        FileConfiguration config = plugin.getItemManager().getCustomItemsConfig();

        // --- 1. Handle treasure items with rarity buckets ---
        Map<Integer, Map<String, Double>> treasureRarityGroups = new TreeMap<>(Collections.reverseOrder());
        double treasureTotalBase = 0.0;

        if (treasureLoot.containsKey(biome)) {
            for (Map.Entry<String, Double> entry : treasureLoot.get(biome).entrySet()) {
                String key = entry.getKey();
                double baseChance = entry.getValue();
                treasureTotalBase += baseChance;

                int rarity = 0;
                if (config.contains("items." + key + ".rarity")) {
                    rarity = config.getInt("items." + key + ".rarity", 0);
                }

                treasureRarityGroups.putIfAbsent(rarity, new LinkedHashMap<>());
                treasureRarityGroups.get(rarity).put(key, baseChance);
            }
        }

        // --- Apply luck scaling to treasure by rarity ---
        double leftoverChance = 1.0;
        for (Map.Entry<Integer, Map<String, Double>> entry : treasureRarityGroups.entrySet()) {
            Map<String, Double> items = entry.getValue();
            double totalBase = items.values().stream().mapToDouble(d -> d).sum();

            double bucketChance = Math.min(totalBase * playerLuck, leftoverChance);
            leftoverChance -= bucketChance;

            for (Map.Entry<String, Double> e : items.entrySet()) {
                double proportion = totalBase == 0 ? 0 : e.getValue() / totalBase;
                finalChances.put(e.getKey(), proportion * bucketChance);
            }
        }

        // --- 2. Distribute remaining chance to junk items ---
        if (junkLoot.containsKey(biome)) {
            Map<String, Double> junkItems = junkLoot.get(biome); // these sum to 1
            for (Map.Entry<String, Double> e : junkItems.entrySet()) {
                // Multiply by leftoverChance (can be 0)
                finalChances.put(e.getKey(), e.getValue() * leftoverChance);
            }
        }
        
        // --- Ensure all junk items are present ---
        if (junkLoot.containsKey(biome)) {
            for (String key : junkLoot.get(biome).keySet()) {
                finalChances.putIfAbsent(key, 0.0);
            }
        }
        return finalChances;
    }

    public Map<String, Double> getDefaultBiomeLoot(String biome) {
        Map<String, Double> finalChances = new LinkedHashMap<>();

        if (!treasureLoot.containsKey(biome) && !junkLoot.containsKey(biome)) return finalChances;

        Map<String, Double> treasureItems = treasureLoot.getOrDefault(biome, Collections.emptyMap());
        Map<String, Double> junkItems = junkLoot.getOrDefault(biome, Collections.emptyMap());

        // 1. Add treasure items as-is
        treasureItems.forEach(finalChances::put);

        // 2. Compute total treasure chance
        double totalTreasureChance = treasureItems.values().stream().mapToDouble(Double::doubleValue).sum();

        // 3. Scale junk items by chance that no treasure drops
        junkItems.forEach((item, chance) -> {
            double effectiveChance = chance * (1 - totalTreasureChance);
            finalChances.put(item, effectiveChance);
        });

        // 4. Ensure all junk items appear even if chance is 0
        junkItems.keySet().forEach(item -> finalChances.putIfAbsent(item, 0.0));

        return finalChances;
    }


    public void trySpawnBoss(Player player) {
        Biome biome = player.getLocation().getBlock().getBiome();
        String biomeKey = biome.name();

        Map<String, Double> bosses = bossOdds.getOrDefault(biomeKey, Collections.emptyMap());
        if (bosses.isEmpty()) return;

        List<Map.Entry<String, Double>> bossEntries = new ArrayList<>(bosses.entrySet());
        Collections.reverse(bossEntries); // reverse boss order

        double ruin = plugin.getPlayerManager().getPlayerRuin(player);

        // 1. Scale boss chances by ruin
        Map<String, Double> scaledBosses = new LinkedHashMap<>();
        double totalScaled = 0;
        for (Map.Entry<String, Double> entry : bossEntries) {
            double scaledChance = entry.getValue() * ruin;
            scaledBosses.put(entry.getKey(), scaledChance);
            totalScaled += scaledChance;
        }

        // 2. Generate a roll between 0 and 1
        double roll = Math.random();

        // 3. If totalScaled > 1, scale chances down to keep ratios
        double scale = totalScaled > 1 ? 1 / totalScaled : 1.0;

        double cumulative = 0;
        for (Map.Entry<String, Double> entry : scaledBosses.entrySet()) {
            cumulative += entry.getValue() * scale;
            if (roll <= cumulative) {
                // Spawn the boss at player's location
                Entity bossEntity = plugin.getBossManager().spawnBoss(
                    entry.getKey(),
                    player,
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ(),
                    player.getWorld()
                );
                if (bossEntity != null) {
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "[RUIN!] " 
                            + ChatColor.RESET + "" + ChatColor.RED + "A boss has appeared!");
                }
                return; // stop after spawning one boss
            }
        }
        // No boss spawned if roll > cumulative
    }

    public Map<String, Double> getBossOdds(String biomeKey, double scaleMultiplier) {
        Map<String, Double> bosses = bossOdds.getOrDefault(biomeKey, Collections.emptyMap());
        Map<String, Double> scaled = new LinkedHashMap<>();

        for (Map.Entry<String, Double> entry : bosses.entrySet()) {
            double scaledChance = entry.getValue() * scaleMultiplier;
            scaled.put(entry.getKey(), scaledChance);
        }

        return scaled;
    }
    public Map<String, Double> getBossOdds(String biomeKey) {
        return getBossOdds(biomeKey, 1.0);
    }

    public void broadcastRareCatch(Player player, ItemStack item, int itemRarity, double itemOdds) {
        // Check if broadcasts are enabled and rarity meets threshold
        if (!plugin.getConfig().getBoolean("broadcast_rare_catches", true)) return;
        int threshold = plugin.getConfig().getInt("announcement_rarity_threshold", 4);
        if (itemRarity < threshold) return;

        // Get rarity info
        RarityInfo rarityInfo = plugin.getItemManager().getRarityMap().get(itemRarity);
        if (rarityInfo == null) return; // safety fallback

        String itemName = item.getItemMeta() != null ? item.getItemMeta().getDisplayName() : item.getType().name();

        // Build message
        String rarityLabel = rarityInfo.label.replace("&", "§"); // convert color codes
        ChatColor rarityColor = rarityInfo.color;

        String message = rarityColor + player.getName()
                + " caught a "
                + rarityLabel + " "
                + ChatColor.RESET + itemName + "!" +
                ChatColor.RESET + "" + ChatColor.BOLD + " (" + formatPercent(itemOdds) + ")";

        // Broadcast
        Bukkit.broadcastMessage(message);
    }
    
    private String formatPercent(double value) {
        if (value == 0) return "0%";
        value = value * 100; // convert to percent

        int magnitude = (int) Math.floor(Math.log10(Math.abs(value))) + 1;

        // Compute scale to get at least 2 significant digits
        int scale = Math.max(0, 2 - magnitude);

        BigDecimal bd = new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString() + "%";
    }

    public void clearLootTables() {
        treasureLoot.clear();
        junkLoot.clear();
        bossOdds.clear();

        // Reload the loot_tables.yml from disk
        if (lootTablesFile != null && lootTablesFile.exists()) {
            lootTablesConfig = YamlConfiguration.loadConfiguration(lootTablesFile);
        }
    }


}
