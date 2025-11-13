package com.dylan.fisheroo;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import com.dylan.fisheroo.armorequip.ArmorEquipEvent;

public class ArmorManager implements Listener {

    private final Main plugin;

    public ArmorManager(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArmorEquip(ArmorEquipEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getPlayerManager().updateStats(player);
        }, 1L);
    }

    public Map<String, Double> getCombinedArmorStats(Player player) {
        Map<String, Double> stats = new HashMap<>();
        stats.put("luck", 0.0);
        stats.put("ruin", 0.0);
        stats.put("multifish", 0.0);

        ItemStack[] armor = player.getInventory().getArmorContents();

        FileConfiguration customItemsConfig = plugin.getItemManager().getCustomItemsConfig();
        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");

        // Keep track of sets worn
        Map<String, Integer> setsWorn = new HashMap<>();

        for (ItemStack item : armor) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
            if (id == null) continue;

            // Find the item key by id
            String itemKey = null;
            for (String key : customItemsConfig.getConfigurationSection("items").getKeys(false)) {
                String yamlId = customItemsConfig.getString("items." + key + ".id", key);
                if (yamlId.equals(id)) {
                    itemKey = key;
                    break;
                }
            }
            if (itemKey == null) continue;

            if (item.getType() == Material.PLAYER_HEAD) {
                String type = customItemsConfig.getString("items." + itemKey + ".type", "");
                if ("artifact".equalsIgnoreCase(type)) {
                    continue; // ? don't apply stats or set bonuses for artifact skulls
                }
            }

            // Add individual item stats
            if (customItemsConfig.isConfigurationSection("items." + itemKey + ".stats")) {
                double luck = customItemsConfig.getDouble("items." + itemKey + ".stats.luck", 0.0);
                double ruin = customItemsConfig.getDouble("items." + itemKey + ".stats.ruin", 0.0);
                double multifish = customItemsConfig.getDouble("items." + itemKey + ".stats.multifish", 0.0);
                stats.put("luck", stats.get("luck") + luck);
                stats.put("ruin", stats.get("ruin") + ruin);
                stats.put("multifish", stats.get("multifish") + multifish);
            }

            // Track set
            String setName = customItemsConfig.getString("items." + itemKey + ".set", null);
            if (setName != null) {
                setsWorn.put(setName, setsWorn.getOrDefault(setName, 0) + 1);
            }
        }

        // Apply set bonuses
        Map<String, Double> setBonus = getArmorSetBonus(player, setsWorn);
        stats.put("luck", stats.get("luck") + setBonus.getOrDefault("luck", 0.0));
        stats.put("ruin", stats.get("ruin") + setBonus.getOrDefault("ruin", 0.0));
        stats.put("multifish", stats.get("multifish") + setBonus.getOrDefault("multifish", 0.0));

        return stats;
    }

    /**
    * Returns the bonus stats for any complete armor sets the player is wearing.
    */
    private Map<String, Double> getArmorSetBonus(Player player, Map<String, Integer> setsWorn) {
        Map<String, Double> bonus = new HashMap<>();
        bonus.put("luck", 0.0);
        bonus.put("ruin", 0.0);
        bonus.put("multifish", 0.0);

        FileConfiguration armorSetsConfig = plugin.getItemManager().getArmorSetsConfig();

        for (String setName : setsWorn.keySet()) {
            int pieces = setsWorn.get(setName);
            // Check if the set exists in the YAML
            if (!armorSetsConfig.isConfigurationSection("armor_sets." + setName)) continue;

            if (pieces == 4) {
                // Full set bonus
                if (armorSetsConfig.isConfigurationSection("armor_sets." + setName + ".stats")) {
                    double luck = armorSetsConfig.getDouble("armor_sets." + setName + ".stats.luck", 0.0);
                    double ruin = armorSetsConfig.getDouble("armor_sets." + setName + ".stats.ruin", 0.0);
                    double multifish = armorSetsConfig.getDouble("armor_sets." + setName + ".stats.multifish", 0.0);
                    bonus.put("luck", bonus.get("luck") + luck);
                    bonus.put("ruin", bonus.get("ruin") + ruin);
                    bonus.put("multifish", bonus.get("multifish") + multifish);
                }
            }
        }

        return bonus;
    }
}