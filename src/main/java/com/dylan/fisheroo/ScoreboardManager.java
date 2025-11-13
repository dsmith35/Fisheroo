package com.dylan.fisheroo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public class ScoreboardManager {

    private final Main plugin;

    private final Map<UUID, BukkitTask> pendingScoreboardUpdates = new HashMap<>();
    private final Map<String, String> lineEntries = new HashMap<>();

    public ScoreboardManager(Main plugin) {
        this.plugin = plugin;
    }


    private String getLineEntry(String lineId) {
        return lineEntries.computeIfAbsent(lineId, id -> "§" + (char) ('a' + lineEntries.size()));
    }

    // --- Scoreboard ---
    public void updateScoreboard(Player player) {
        Scoreboard board = createBoard();
        Component emptyLine = Component.text(" ");

        // --- Title ---
        TextColor[] rainbow = new TextColor[]{
            TextColor.color(255, 0, 0),       // Red
            TextColor.color(255, 127, 0),     // Orange
            TextColor.color(255, 255, 0),     // Yellow
            TextColor.color(0, 255, 0),       // Green
            TextColor.color(0, 0, 255),       // Blue
            TextColor.color(75, 0, 130),      // Indigo
            TextColor.color(148, 0, 211)      // Violet
        };
        setGradientTitle(board, "fisherooInfo", "Fisheroo.minehut.gg", rainbow);

        // --- Stats lines ---
        Map<String, Double> stats = plugin.getPlayerManager().getTotalStats(player);

        // Luck line
        Component luckLine = buildGradientComponent(
            formatStatText(player, "luck", stats.get("luck")),
            TextColor.color(0, 255, 0),
            TextColor.color(0, 200, 0)
        );
        setGradientLine(board, "luck", luckLine, 0);

        // Ruin line
        Component ruinLine = buildGradientComponent(
            formatStatText(player, "ruin", stats.get("ruin")),
            TextColor.color(255, 0, 0),
            TextColor.color(200, 0, 0)
        );
        setGradientLine(board, "ruin", ruinLine, 0);

        // Multifish line
        Component multifishLine = buildGradientComponent(
            formatStatTextNoDecimals(player, "multifish", stats.get("multifish")),
            TextColor.color(162, 50, 168),
            TextColor.color(205, 70, 212)
        );
        setGradientLine(board, "multifish", multifishLine, 0);

        // Biome line
        Component prefix = Component.text("Biome: ", NamedTextColor.WHITE);
        Component biomeName = plugin.getBiomeManager().getCustomBiomeComponent(player.getLocation().getBlock().getBiome().name());
        Component biomeLine = prefix.append(biomeName);
        setGradientLine(board, "biome", biomeLine, 0);

      // --- Weather line ---
        WeatherManager.WeatherType currentWeather = plugin.getWeatherManager().getCurrentWeather();
        int remainingSeconds = plugin.getWeatherManager().getRemainingSeconds();
        Component weatherLine = Component.text("Weather: ", NamedTextColor.WHITE)
                .append(Component.text(currentWeather.getDisplayName(), NamedTextColor.AQUA));
        if (currentWeather != WeatherManager.WeatherType.CLEAR_SKIES) {
            weatherLine = weatherLine.append(
                Component.text(" (" + remainingSeconds + "s)", NamedTextColor.GRAY)
            );
        }

        setGradientLine(board, "weather", weatherLine, 0);

        //empty
        setGradientLine(board, "empty1", emptyLine, 0);
        
        //discord
        Component discordLine = Component.text("/discord", NamedTextColor.AQUA);
        setGradientLine(board, "discord", discordLine, 0);

        // Apply scoreboard
        player.setScoreboard(board);
    }

    private String formatStatText(Player player, String statName, double value) {
        String text = statName.substring(0, 1).toUpperCase() + statName.substring(1) + ": " + String.format("%.2f", value) + "x";
        if (plugin.getPlayerManager().getManualStats(player).containsKey(statName)) {
            text += " (M)";
        }
        return text;
    }
    private String formatStatTextNoDecimals(Player player, String statName, double value) {
        String text = statName.substring(0, 1).toUpperCase() + statName.substring(1) + ": " + (int) value + "x";
        if (plugin.getPlayerManager().getManualStats(player).containsKey(statName)) {
            text += " (M)";
        }
        return text;
    }

    private Scoreboard createBoard() {
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        return (manager == null) ? Bukkit.getScoreboardManager().getNewScoreboard() : manager.getNewScoreboard();
    }
    public void setGradientTitle(Scoreboard board, String objectiveName, String text, TextColor... colors) {
        Component gradientTitle = buildGradientComponent(text, colors);

        Objective obj = board.getObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            obj = board.registerNewObjective(objectiveName, "dummy", gradientTitle);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(gradientTitle);
        }
    }


    private void setGradientLine(Scoreboard board, String lineId, Component textComponent, int scoreValue) {
        Objective obj = board.getObjective(DisplaySlot.SIDEBAR);
        if (obj == null) obj = board.registerNewObjective("dummyObj", "dummy", Component.text("Scores"));

        Team team = board.getTeam(lineId);
        if (team == null) team = board.registerNewTeam(lineId);

        String entry = getLineEntry(lineId);
        if (!team.hasEntry(entry)) team.addEntry(entry);

        team.prefix(textComponent);
        obj.getScore(entry).setScore(scoreValue);
    }

    private Component buildGradientComponent(String text, TextColor... colors) {
        Component result = Component.empty();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            double progress = (len == 1) ? 0 : (double) i / (len - 1);
            int segment = (int) Math.floor(progress * (colors.length - 1));
            double localProgress = (progress * (colors.length - 1)) - segment;

            TextColor start = colors[segment];
            TextColor end = colors[Math.min(segment + 1, colors.length - 1)];

            int r = (int) (start.red() + (end.red() - start.red()) * localProgress);
            int g = (int) (start.green() + (end.green() - start.green()) * localProgress);
            int b = (int) (start.blue() + (end.blue() - start.blue()) * localProgress);

            result = result.append(Component.text(String.valueOf(text.charAt(i)), TextColor.color(r, g, b)));
        }
        return result;
    }

    public void scheduleScoreboardUpdate(Player player) {
        // Schedules a delayed scoreboard update for the player, canceling any previously queued update to prevent redundant refreshes
        // Any future calls within that tickDelay will cancel prior calls that havent executed yet

        Long tickDelay = 5L;
        UUID playerId = player.getUniqueId();

        // Cancel previous pending task if it exists
        if (pendingScoreboardUpdates.containsKey(playerId)) {
            pendingScoreboardUpdates.get(playerId).cancel();
        }

        // Schedule a new scoreboard update
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            updateScoreboard(player);
            pendingScoreboardUpdates.remove(playerId);
        }, tickDelay); // delay in ticks

        pendingScoreboardUpdates.put(playerId, task);
    }

    public void startUpdatingScoreboard(Player player, long tickFreq) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateScoreboard(player);
        }, 0L, tickFreq); // 0 tick delay, 20 ticks = 1 second
    }

}
