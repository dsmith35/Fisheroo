package com.dylan.fisheroo;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

public class BiomeManager {

    private final Main plugin;

    private File customBiomesFile;
    private FileConfiguration customBiomesConfig;

    public BiomeManager(Main plugin) {
        this.plugin = plugin;
    }

    public String getCustomBiomeName(String biomeKey) {
        String name = biomeKey;
        if (customBiomesConfig.isConfigurationSection("biomes")) {
            String configured = customBiomesConfig.getString("biomes." + biomeKey);
            if (configured != null) name = ChatColor.translateAlternateColorCodes('&', configured);
        }
        return name;
    }

    public Component getCustomBiomeComponent(String biomeKey) {
        String name = biomeKey;
        if (customBiomesConfig.isConfigurationSection("biomes")) {
            String configured = customBiomesConfig.getString("biomes." + biomeKey);
            if (configured != null) name = configured;
        }

        // Translate & codes to §
        name = ChatColor.translateAlternateColorCodes('&', name);

        // Convert legacy text with colors to Component
        return Component.text()
                .append(Component.text(name))
                .build();
    }

    public void loadBiomes() {
        customBiomesFile = new File(plugin.getDataFolder(), "custom_biomes.yml");
        if (!customBiomesFile.exists()) plugin.saveResource("custom_biomes.yml", false);
        customBiomesConfig = YamlConfiguration.loadConfiguration(customBiomesFile);
    }

    // --- Check if player has access to biome ---
    public boolean hasAccess(Player player, Biome biome) {
        // TODO: replace with real item/access check
        // For now, all players have no access
        return false;
    }

    // --- Fling player out of biome if they have no access ---
    public void checkAndFling(Player player) {
        Location loc = player.getLocation();
        Biome currentBiome = loc.getBlock().getBiome();

        if (!hasAccess(player, currentBiome)) {
            // Simple fling: push opposite to movement
            Vector direction = player.getVelocity().clone().normalize();
            if (direction.length() == 0) direction = new Vector(0, 0, 1);
            direction.setY(0.5); // vertical lift
            player.setVelocity(direction.multiply(1.5));

            // Optional: message
            player.sendMessage(ChatColor.RED + "You cannot enter " + getCustomBiomeName(currentBiome.name()) + "!");
        }
    }

    public void clearBiomes() {
        if (customBiomesConfig != null) {
            customBiomesConfig = null;
        }
        if (customBiomesFile != null) {
            customBiomesFile = null;
        }
    }

}
