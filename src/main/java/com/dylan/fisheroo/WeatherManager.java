package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WeatherManager {

    private final Main plugin;
    
    private final Random random = new Random();
    private WeatherType currentWeather = WeatherType.CLEAR_SKIES;
    private int remainingSeconds = 0; // counts down every second
    private WeatherType lastBroadcastedWeather = null;
    
    public WeatherManager(Main plugin) {
        this.plugin = plugin;
    }

    public enum WeatherType {
        CLEAR_SKIES("Clear Skies"),
        RAIN("Rain"),
        STORM("Storm"),
        HURRICANE("Hurricane"),
        RAINBOW("Rainbow"),
        METEOR_SHOWER("Meteor Shower");

        private final String displayName;

        WeatherType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static class WeatherEffect {
        public final Map<String, Double> bonuses;
        public final Map<String, Double> multipliers;
        public final int durationSeconds;

        public WeatherEffect(Map<String, Double> bonuses, Map<String, Double> multipliers, int durationSeconds) {
            this.bonuses = bonuses;
            this.multipliers = multipliers;
            this.durationSeconds = durationSeconds;
        }
    }

    private final Map<WeatherType, WeatherEffect> weatherEffects = new HashMap<>() {{
        put(WeatherType.CLEAR_SKIES, new WeatherEffect(
                Map.of("luck", 0.0, "ruin", 0.0, "multifish", 0.0),
                Map.of("luck", 1.0, "ruin", 1.0, "multifish", 1.0),
                300 // 5 minutes
        ));
        put(WeatherType.RAIN, new WeatherEffect(
                Map.of("luck", 0.5),
                Map.of(),
                600 // 10 minutes
        ));
        put(WeatherType.STORM, new WeatherEffect(
                Map.of(),
                Map.of("ruin", 2.0),
                600
        ));
        put(WeatherType.HURRICANE, new WeatherEffect(
                Map.of(),
                Map.of("luck", 1.5, "ruin", 1.5),
                600
        ));
        put(WeatherType.RAINBOW, new WeatherEffect(
                Map.of(),
                Map.of("luck", 2.0),
                300
        ));
        put(WeatherType.METEOR_SHOWER, new WeatherEffect(
                Map.of(),
                Map.of("luck", 5.0, "ruin", 5.0),
                180
        ));
    }};

    public void rollRandomWeather() {
        double roll = random.nextDouble() * 100;

        WeatherType chosen;
        if (roll <= 70) chosen = WeatherType.CLEAR_SKIES;
        else if (roll <= 85) chosen = WeatherType.RAIN;
        else if (roll <= 92) chosen = WeatherType.STORM;
        else if (roll <= 95.5) chosen = WeatherType.HURRICANE;
        else if (roll <= 99) chosen = WeatherType.RAINBOW;
        else chosen = WeatherType.METEOR_SHOWER;

        applyWeather(chosen);
    }

    public void applyWeather(WeatherType type) {
        WeatherEffect effect = weatherEffects.get(type);
        if (effect == null) return;

        // Store current weather and duration
        currentWeather = type;
        remainingSeconds = effect.durationSeconds;

        // Reset global modifiers
        plugin.getPlayerManager().clearGlobalModifiers();

        // Apply bonuses
        for (Map.Entry<String, Double> bonus : effect.bonuses.entrySet()) {
            plugin.getPlayerManager().setGlobalBonus(bonus.getKey(), bonus.getValue());
        }

        // Apply multipliers
        for (Map.Entry<String, Double> mult : effect.multipliers.entrySet()) {
            plugin.getPlayerManager().setGlobalMultiplier(mult.getKey(), mult.getValue());
        }

        // Broadcast message only if it’s not CLEAR_SKIES twice in a row
        if (type != WeatherType.CLEAR_SKIES || lastBroadcastedWeather != WeatherType.CLEAR_SKIES) {
            String message = switch (type) {
                case CLEAR_SKIES -> "§f§l[WEATHER] §r§eThe skies clear. Fishing returns to normal!";
                case RAIN -> "§f§l[WEATHER] §r§bIt starts raining... Luck +50%!";
                case STORM -> "§f§l[WEATHER] §r§3A storm rages... Ruin ×2!";
                case HURRICANE -> "§f§l[WEATHER] §r§9A hurricane blows in... Luck ×1.5, Ruin ×1.5!";
                case RAINBOW -> "§f§l[WEATHER] §r§dA rainbow appears... Luck ×2!";
                case METEOR_SHOWER -> "§f§l[WEATHER] §r§6A meteor shower rains down... Luck ×5, Ruin ×5!";
            };
            Bukkit.broadcastMessage(message);
            lastBroadcastedWeather = type;
        }

        // Apply actual weather in all worlds
        for (World world : Bukkit.getWorlds()) {
            switch (type) {
                case CLEAR_SKIES -> {
                    world.setStorm(false);
                    world.setThundering(false);
                    world.setWeatherDuration(effect.durationSeconds * 20);
                }
                case RAIN -> {
                    world.setStorm(true);
                    world.setThundering(false);
                    world.setWeatherDuration(effect.durationSeconds * 20);
                }
                case STORM, HURRICANE, RAINBOW, METEOR_SHOWER -> {
                    world.setStorm(true);
                    world.setThundering(true);
                    world.setWeatherDuration(effect.durationSeconds * 20);
                }
            }
        }

        // Countdown task for remainingSeconds
        new BukkitRunnable() {
        @Override
        public void run() {
            if (remainingSeconds <= 0) {
                // If the last weather wasn’t CLEAR_SKIES, force CLEAR_SKIES first
                if (currentWeather != WeatherType.CLEAR_SKIES) {
                    applyWeather(WeatherType.CLEAR_SKIES);
                } else {
                    rollRandomWeather(); // normal random roll
                }
                cancel();
                return;
            }
            remainingSeconds--;
        }
    }.runTaskTimer(plugin, 20L, 20L);
    }


    // Optional: call this to start weather system on plugin enable
    public void startWeatherCycle() {
        rollRandomWeather();
    }

    public WeatherType getCurrentWeather() {
    return currentWeather;
}

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

}
