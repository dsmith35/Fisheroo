package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;

import java.io.File;

public class WarpManager implements Listener {

    private final Main plugin;

    private File warpsFile;
    private FileConfiguration warpsConfig;

    public WarpManager(Main plugin) {
        this.plugin = plugin;

        // Load warps.yml
        warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        if (!warpsFile.exists()) {
            plugin.saveResource("warps.yml", false);
        }
        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
    }

    public boolean warpPlayer(Player player, String warpName) {
        Location loc = getWarpLocation(warpName);
        if (loc == null) {
            player.sendMessage("§cWarp '" + warpName + "' does not exist or is invalid!");
            return false;
        }

        player.teleport(loc);
        return true;
    }

    public Location getWarpLocation(String warpName) {
        if (!warpsConfig.contains("warps")) return null;

        String matchedKey = null;
        for (String key : warpsConfig.getConfigurationSection("warps").getKeys(false)) {
            if (key.equalsIgnoreCase(warpName)) {
                matchedKey = key;
                break;
            }
        }
        if (matchedKey == null) return null;

        String locString = warpsConfig.getString("warps." + matchedKey);
        if (locString == null) return null;

        String[] parts = locString.split(",");
        if (parts.length < 3) return null;

        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = parts.length >= 4 ? Float.parseFloat(parts[3]) : 0f;
            float pitch = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0f;

            return new Location(Bukkit.getWorld("world"), x + 0.5, y, z + 0.5, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    public FileConfiguration getWarpsConfig() {
        return warpsConfig;
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

        // Get warp from custom_items.yml
        String warp = plugin.getItemManager().getCustomItemsConfig().getString("items." + yamlKey + ".warp");
        if (warp == null || warp.isEmpty()) return;

        // Cancel normal interaction
        event.setCancelled(true);

        // Warp the player
        plugin.getWarpManager().warpPlayer(event.getPlayer(), warp);
    }

}
