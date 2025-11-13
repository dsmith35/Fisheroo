package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import com.dylan.fisheroo.boss.BossLootEntry;
import com.dylan.fisheroo.boss.BossSpawner;
import com.dylan.fisheroo.boss.Boss;

import java.io.File;
import java.util.*;

public class BossManager implements Listener {

    private final Map<String, Boss> bosses = new HashMap<>();
    private final Main plugin;
    private final Random random = new Random();
    public BossSpawner bossSpawner;
    private final int spawnerTickInterval = 20; // in game ticks between spawn checks for boss spawner

    public BossManager(Main plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin); // register listener

        this.bossSpawner = new BossSpawner(this, spawnerTickInterval);
    }

    public void loadBosses() {
        File file = new File(plugin.getDataFolder(), "bosses.yml");
        if (!file.exists()) plugin.saveResource("bosses.yml", false);

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        bosses.clear();

        if (config.contains("bosses")) {
            for (String bossId : config.getConfigurationSection("bosses").getKeys(false)) {
                String rawName = config.getString("bosses." + bossId + ".name", bossId);
                String displayName = ChatColor.translateAlternateColorCodes('&', rawName);
                String typeStr = config.getString("bosses." + bossId + ".type", "ZOMBIE");
                Double hp = config.getDouble("bosses." + bossId + ".hp", 20f);
                EntityType type = EntityType.valueOf(typeStr);
                double size = config.getDouble("bosses." + bossId + ".size", 1.0);
                int rarity = config.getInt("bosses." + bossId + ".rarity", 0);

                List<BossLootEntry> drops = new ArrayList<>();
                if (config.contains("bosses." + bossId + ".drops")) {
                    for (Object obj : config.getList("bosses." + bossId + ".drops")) {
                        if (!(obj instanceof Map<?, ?> map)) continue;

                        String itemKey = (String) map.get("item");
                        int min = map.containsKey("min") ? (int) map.get("min") : 1;
                        int max = map.containsKey("max") ? (int) map.get("max") : min;
                        double chance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 1.0;

                        drops.add(new BossLootEntry(itemKey, min, max, chance));
                    }
                }

                Map<String, String> gear = new HashMap<>();
                if (config.contains("bosses." + bossId + ".gear")) {
                    ConfigurationSection gearSec = config.getConfigurationSection("bosses." + bossId + ".gear");
                    if (gearSec != null) {
                        for (String slot : gearSec.getKeys(false)) {
                            String itemKey = gearSec.getString(slot);
                            if (itemKey != null && !itemKey.equalsIgnoreCase("null")) {
                                gear.put(slot.toLowerCase(), itemKey);
                            }
                        }
                    }
                }

                Map<String, Object> nbtData = new HashMap<>();
                if (config.contains("bosses." + bossId + ".nbt")) {
                    ConfigurationSection nbtSec = config.getConfigurationSection("bosses." + bossId + ".nbt");
                    if (nbtSec != null) {
                        for (String key : nbtSec.getKeys(false)) {
                            nbtData.put(key, nbtSec.get(key));
                        }
                    }
                }

                bosses.put(bossId, new Boss(bossId, displayName, type, drops, hp, gear, size, rarity, nbtData));
            }
        }
    }

    public Boss getBoss(String id) {
        return bosses.get(id);
    }

    public Map<String, Boss> getBosses() {
        return bosses;
    }

    // -------------------- Loot on Boss Death --------------------
    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntity().getCustomName() == null) return;

        String name = ChatColor.stripColor(event.getEntity().getCustomName());

        Boss boss = bosses.values().stream()
                .filter(b -> ChatColor.stripColor(b.getDisplayName()).equals(name))
                .findFirst()
                .orElse(null);

        if (boss == null) return;

        // Clear default drops
        event.getDrops().clear();

        Player recipient = null;

        // Try summoner first
        if (event.getEntity().hasMetadata("summoner")) {
            recipient = Bukkit.getPlayer((UUID) event.getEntity().getMetadata("summoner").get(0).value());
        }

        // Fallback to killer if no summoner
        if (recipient == null) {
            recipient = event.getEntity().getKiller();
        }

        if (recipient != null) {
            final Player finalRecipient = recipient;
            for (BossLootEntry entry : boss.getDrops()) {
                if (random.nextDouble() <= entry.getChance()) {
                    int amount = entry.getMin() + random.nextInt(entry.getMax() - entry.getMin() + 1);
                    ItemStack item = plugin.getItemManager().getCustomItem(entry.getItemKey());
                    if (item != null) {
                        item.setAmount(amount);
                        finalRecipient.getInventory().addItem(item);
                        finalRecipient.sendMessage(ChatColor.GREEN + "You received " + amount + " x " + item.getItemMeta().getDisplayName());
                        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(finalRecipient), 1L); // update stats
                    }
                }
            }
        }
    }

    public Entity spawnBoss(String bossId, Player summoner, double x, double y, double z, World world) {
        Boss boss = getBoss(bossId);
        if (boss == null) return null;

        if (summoner != null && boss.getRarity() < plugin.getPlayerManager().getMinBossRarity(summoner)) {
            return null;
        }

        // Spawn the entity
        Entity entity = world.spawnEntity(new Location(world, x, y, z), boss.getType());

        // Set custom name
        entity.setCustomName(boss.getDisplayName());
        entity.setCustomNameVisible(true);

        // Set custom HP if it's a living entity
        if (entity instanceof LivingEntity living) {
            var healthAttr = living.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(boss.getHp());
                living.setHealth(boss.getHp());
            }
            living.setRemoveWhenFarAway(false);

            // Apply custom size (default 1.0 if not set)
            var scaleAttr = living.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(boss.getSize());
            }
            
            // Apply gear
            if (!boss.getGear().isEmpty()) {
                org.bukkit.inventory.EntityEquipment eq = living.getEquipment();
                if (eq != null) {
                    for (Map.Entry<String, String> entry : boss.getGear().entrySet()) {
                        String slot = entry.getKey();
                        String itemKey = entry.getValue();

                        // Try to get custom item first
                        ItemStack gearItem = plugin.getItemManager().getCustomItem(itemKey);

                        // If custom item doesn't exist, try vanilla Material
                        if (gearItem == null) {
                            try {
                                Material mat = Material.valueOf(itemKey.toUpperCase());
                                gearItem = new ItemStack(mat);
                            } catch (IllegalArgumentException ignored) {
                                // Neither custom nor vanilla exists; skip
                                continue;
                            }
                        }

                        // Apply the item to the correct slot
                        switch (slot) {
                            case "helmet" -> eq.setHelmet(gearItem);
                            case "chestplate" -> eq.setChestplate(gearItem);
                            case "leggings" -> eq.setLeggings(gearItem);
                            case "boots" -> eq.setBoots(gearItem);
                            case "mainhand" -> eq.setItemInMainHand(gearItem);
                            case "offhand" -> eq.setItemInOffHand(gearItem);
                        }
                    }
                }
            }

            // Apply NBT if TropicalFish
            if (entity instanceof org.bukkit.entity.TropicalFish fish) {
                Map<String, Object> nbt = boss.getNbtData();

                // Body Color
                if (nbt.containsKey("body_color")) {
                    try {
                        org.bukkit.DyeColor color = org.bukkit.DyeColor.valueOf(
                            nbt.get("body_color").toString().toUpperCase()
                        );
                        fish.setBodyColor(color);
                    } catch (IllegalArgumentException ignored) {}
                }

                // Pattern Color
                if (nbt.containsKey("pattern_color")) {
                    try {
                        org.bukkit.DyeColor patternColor = org.bukkit.DyeColor.valueOf(
                            nbt.get("pattern_color").toString().toUpperCase()
                        );
                        fish.setPatternColor(patternColor);
                    } catch (IllegalArgumentException ignored) {}
                }

                // Pattern
                if (nbt.containsKey("pattern")) {
                    try {
                        org.bukkit.entity.TropicalFish.Pattern pattern =
                            org.bukkit.entity.TropicalFish.Pattern.valueOf(
                                nbt.get("pattern").toString().toUpperCase()
                            );
                        fish.setPattern(pattern);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }


        // Attach summoner as metadata
        if (summoner != null) {
            entity.setMetadata("summoner", new FixedMetadataValue(plugin, summoner.getUniqueId()));
        }

        return entity;
    }


    public FileConfiguration getNaturalSpawnConfig() {
        File file = new File(plugin.getDataFolder(), "natural_spawn.yml");
        if (!file.exists()) plugin.saveResource("natural_spawn.yml", false);
        return YamlConfiguration.loadConfiguration(file);
    }

    public World getSpawnWorld() {
        // For simplicity, we just return the first world. Can extend later.
        return Bukkit.getWorlds().get(0);
    }

    public Main getPlugin() {
        return plugin;
    }

    public void clearBosses() {
        bosses.clear();           // Remove all loaded bosses
        bossSpawner = null;       // Optionally clear the spawner if you want to reset it
    }


    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        // Only right-click actions
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        // Use ItemManager to check if this is a custom item
        String itemId = plugin.getItemManager().getItemIdFromItem(item);
        if (itemId == null) return;

        String yamlKey = plugin.getItemManager().getYamlKeyFromId(itemId);
        if (yamlKey == null) return;

        String summon = plugin.getItemManager().getCustomItemsConfig().getString("items." + yamlKey + ".summon");
        if (summon == null || summon.isEmpty()) return;

        Player player = event.getPlayer();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        // Spawn boss
        spawnBoss(summon, player, x, y, z, world);

        // Remove the item from player's hand
        if (event.getHand() == EquipmentSlot.HAND) { // main hand
            player.getInventory().setItemInMainHand(null);
        } else if (event.getHand() == EquipmentSlot.OFF_HAND) { // offhand
            player.getInventory().setItemInOffHand(null);
        }

        // Cancel normal interaction
        event.setCancelled(true);
    }


    
}
