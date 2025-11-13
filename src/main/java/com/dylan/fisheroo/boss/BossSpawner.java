package com.dylan.fisheroo.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import com.dylan.fisheroo.BossManager;

public class BossSpawner {

    private final BossManager bossManager;
    private final Random random = new Random();
    private final Map<String, Long> lastSpawnAttemptTime = new HashMap<>();
    private final int tickInterval; // interval between task runs in game ticks
    private long serverTick = 0;

    public BossSpawner(BossManager bossManager, int tickInterval) {
        this.bossManager = bossManager;
        this.tickInterval = tickInterval;
        startSpawnTask();
    }

    private void startSpawnTask() {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                serverTick += tickInterval; // increment by the interval

                FileConfiguration config = bossManager.getNaturalSpawnConfig();
                if (!config.contains("natural_spawn")) return;

                for (String bossId : config.getConfigurationSection("natural_spawn").getKeys(false)) {
                    attemptBossSpawn(bossId, config.getConfigurationSection("natural_spawn." + bossId));
                }
            }
        }.runTaskTimer(bossManager.getPlugin(), 0L, tickInterval);
    }

    private void attemptBossSpawn(String yamlBossId, org.bukkit.configuration.ConfigurationSection section) {
        World world = bossManager.getSpawnWorld();
        if (world == null || world.getPlayers().isEmpty()) return;

        // --- Get actual boss ID from YAML ---
        String bossId = section.getString("boss");
        if (bossId == null) {
            Bukkit.getLogger().warning("BossSpawner: YAML boss section '" + yamlBossId + "' missing 'boss' field!");
            return;
        }

        // --- Fetch boss from BossManager ---
        Boss boss = bossManager.getBoss(bossId);
        if (boss == null) {
            Bukkit.getLogger().warning("BossSpawner: Boss ID '" + bossId + "' not found in BossManager!");
            return;
        }

        int frequency = section.getInt("frequency", 600); // ticks between attempts
        double chance = section.getDouble("chance", 0.2); // chance per cycle
        int maxNearby = section.getInt("maxNearby", 5);
        int radius = section.getInt("radius", 30);
        int minDepth = section.getInt("minDepth", 3);
        int maxAttempts = section.getInt("maxAttemptsPerCycle", 3);

        long now = serverTick;
        long lastTime = lastSpawnAttemptTime.getOrDefault(yamlBossId, 0L);

        // --- Respect frequency ---
        if (now - lastTime < frequency) return;
        lastSpawnAttemptTime.put(yamlBossId, now);

        // --- Roll chance ---
        if (random.nextDouble() > chance) return;

        // --- Try to find a valid spawn location ---
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Location loc = getRandomPlayerRelativeLocation(world, section.getString("biome", "BEACH"), radius);
            if (loc == null) continue;

            if (!isMostlyDeepWater(loc, minDepth, 0.7)) continue;

            // --- Count nearby bosses safely ---
            long nearby = world.getNearbyEntities(loc, radius, radius, radius,
                    e -> e.getCustomName() != null && e.getCustomName().equalsIgnoreCase(boss.getDisplayName()))
                    .size();
            if (nearby >= maxNearby) continue;

            // --- Found valid location, spawn boss ---
            bossManager.spawnBoss(bossId, null, loc.getX(), loc.getY(), loc.getZ(), world);
            Bukkit.getLogger().info("BossSpawner: Spawned '" + boss.getDisplayName() + "' at " + loc);
            break; // spawn only one per cycle
        }
    }


    private Location getRandomPlayerRelativeLocation(World world, String biomeName, int radius) {
        Player player = world.getPlayers().get(random.nextInt(world.getPlayers().size()));
        Location playerLoc = player.getLocation();

        int minDistance = 5;  // minimum distance from the player
        int maxDistance = radius;

        for (int attempt = 0; attempt < 50; attempt++) { // try up to 50 times
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);

            int dx = (int) Math.round(Math.cos(angle) * distance);
            int dz = (int) Math.round(Math.sin(angle) * distance);

            int x = playerLoc.getBlockX() + dx;
            int z = playerLoc.getBlockZ() + dz;

            int y = world.getHighestBlockYAt(x, z); // get surface y
            Location loc = new Location(world, x + 0.5, y, z + 0.5);

            // Check biome
            if (!loc.getBlock().getBiome().name().equalsIgnoreCase(biomeName)) continue;

            // Check that surface is not solid (so boss can spawn)
            if (loc.getBlock().getType().isSolid()) continue;

            return loc; // found a valid location
        }

        return null; // no valid location found after 50 attempts
    }

    private boolean isDeepWater(Location loc, int minDepth) {
        World world = loc.getWorld();
        int baseX = loc.getBlockX();
        int baseZ = loc.getBlockZ();
        int baseY = loc.getBlockY();

        // Check each column in 3x3 area
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int waterDepth = 0;
                for (int y = baseY; y >= 0; y--) {
                    Block block = world.getBlockAt(baseX + dx, y, baseZ + dz);
                    if (block.getType() == Material.WATER) {
                        waterDepth++;
                        if (waterDepth >= minDepth) break;
                    } else {
                        break; // stop counting this column
                    }
                }
                if (waterDepth < minDepth) return false;
            }
        }
        return true;
    }

    private boolean isMostlyDeepWater(Location loc, int minDepth, double requiredFraction) {
        World world = loc.getWorld();
        int baseX = loc.getBlockX();
        int baseZ = loc.getBlockZ();
        int baseY = loc.getBlockY();

        int totalColumns = 0;
        int deepColumns = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                totalColumns++;
                int waterDepth = 0;
                for (int y = baseY; y >= 0; y--) {
                    Block block = world.getBlockAt(baseX + dx, y, baseZ + dz);
                    if (block.getType() == Material.WATER) {
                        waterDepth++;
                        if (waterDepth >= minDepth) break; // column deep enough
                    } else {
                        break; // stop counting this column
                    }
                }
                if (waterDepth >= minDepth) deepColumns++;
            }
        }

        double fraction = (double) deepColumns / totalColumns;
        return fraction >= requiredFraction; // e.g., 0.7 = 70% columns must be deep
    }


}
