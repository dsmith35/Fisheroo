package com.dylan.fisheroo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import com.dylan.fisheroo.boss.BossLootEntry;
import com.dylan.fisheroo.boss.BossSpawner;
import com.dylan.fisheroo.boss.Boss;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.dylan.fisheroo.util.SkullUtil;


public class CommandManager {

    private final Main plugin;

    private final List<String> validStats = List.of("luck", "ruin"); // add more stats if needed

    public CommandManager(Main plugin) {
        this.plugin = plugin;
    }


    private boolean customFishCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (!(sender instanceof Player executor)) {
            sender.sendMessage("Only players can use this command without specifying a target!");
            return true;
        }

        int times = 1; // default
        Player target = executor; // default target

        if (args.length >= 1) {
            try {
                times = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number: " + args[0]);
                return true;
            }

            if (args.length >= 2) {
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                    return true;
                }
            }
        }

        for (int i = 0; i < times; i++) {
            plugin.getLootManager().doCustomFish(target);
        }

        sender.sendMessage(ChatColor.GREEN + "Gave " + times + " fish to " + target.getName() + "!");
        return true;
    }

    private boolean setStatCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /setstat <player> [stat] [value]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found!");
            return true;
        }

        if (args.length == 1) {
            // Clear all manual stats
            plugin.getPlayerManager().clearAllManualStats(target);
            plugin.getScoreboardManager().updateScoreboard(target);
            sender.sendMessage(ChatColor.GREEN + "Cleared all stat overrides for " + target.getName());
            return true;
        }

        String stat = args[1].toLowerCase();
        if (!validStats.contains(stat)) {
            sender.sendMessage(ChatColor.RED + "Unknown stat: " + stat);
            return true;
        }

        if (args.length == 2) {
            // Clear single stat
            plugin.getPlayerManager().clearManualStat(target, stat);
            plugin.getScoreboardManager().updateScoreboard(target);
            sender.sendMessage(ChatColor.GREEN + "Cleared " + stat + " override for " + target.getName());
            return true;
        }

        try {
            double value = Double.parseDouble(args[2]);
            // Set manual stat override
            plugin.getPlayerManager().setManualStat(target, stat, value);
            plugin.getScoreboardManager().updateScoreboard(target);
            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s " + stat + " to " + value);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[2]);
        }

        return true;
    }

    private List<String> setStatTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1) {
        // Suggest online player names
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return names;
    }

    if (args.length == 2) {
        // Suggest stats
        return validStats;
    }

    return Collections.emptyList();
}



    private boolean updateItemsCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        int updated = plugin.getItemManager().updatePlayerItems(player);
        if (updated > 0) {
            player.sendMessage(ChatColor.GREEN + "Updated " + updated + " custom item(s) to the latest version!");
        }
        return true;
    }

    private boolean giveItemCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /giveitem [player] <itemKey> [amount]");
            return true;
        }

        Player target;
        String itemKeyInput;
        int amount = 1;

        if (args.length == 1) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.RED + "Only players can use this without specifying a target.");
                return true;
            }
            target = p;
            itemKeyInput = args[0];
        } else if (args.length == 2) {
            Player potential = Bukkit.getPlayer(args[0]);
            if (potential != null) {
                target = potential;
                itemKeyInput = args[1];
            } else {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Only players can give items to themselves.");
                    return true;
                }
                target = p;
                itemKeyInput = args[0];
                try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
            }
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
            itemKeyInput = args[1];
            try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }

        ItemStack item = plugin.getItemManager().getCustomItems().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(itemKeyInput))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Custom item not found: " + itemKeyInput);
            return true;
        }

        ItemStack clone = item.clone();
        clone.setAmount(amount);
        target.getInventory().addItem(clone);
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getPlayerManager().updateStats(target), 1L); // update stats

        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " x " + item.getItemMeta().getDisplayName() 
            + ChatColor.RESET + "" + ChatColor.GREEN + " to " + target.getName());
        if (!sender.equals(target)) {
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " x " + item.getItemMeta().getDisplayName() + ChatColor.RESET + "" + ChatColor.GREEN + "!");
        }
        return true;
    }

    private List<String> giveItemTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(plugin.getItemManager().getCustomItems().keySet().stream()
                    .map(String::toLowerCase)
                    .filter(key -> key.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList());
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList());
            return suggestions;
        } else if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                return plugin.getItemManager().getCustomItems().keySet().stream()
                        .map(String::toLowerCase)
                        .filter(key -> key.startsWith(args[1].toLowerCase()))
                        .sorted()
                        .toList();
            } else return Collections.emptyList();
        } else return Collections.emptyList();
    }

    private boolean summonBossCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /summonboss <boss>");
            return true;
        }

        String bossId = args[0].toUpperCase();
        Entity entity = plugin.getBossManager().spawnBoss(
            bossId, 
            null, // no summoner for commannd summoned bosses so whoever kills gets loot
            player.getLocation().getX(), 
            player.getLocation().getY(), 
            player.getLocation().getZ(),
            player.getWorld()
        );

        if (entity == null) {
            player.sendMessage(ChatColor.RED + "Boss not found: " + bossId);
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Spawned boss: " + entity.getCustomName());
        return true;
    }


    private List<String> summonBossTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) return Collections.emptyList();
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (String bossId : plugin.getBossManager().getBosses().keySet()) {
                if (bossId.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(bossId.toLowerCase());
                }
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

    private boolean lootBreakdownCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        // --- Handle /odds boss <bossId> ---
        if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            String bossId = args[1];
            showSpecificBossLoot(player, bossId);
            return true;
        }

        // Get biome and display name
        String biomeKey = player.getLocation().getBlock().getBiome().name();
        String biomeDisplay = plugin.getBiomeManager().getCustomBiomeName(biomeKey);

        // Get luck
        double defaultLuck = plugin.getConfig().getDouble("luck_multiplier_default", 1.0);
        double luck = plugin.getPlayerManager().getPlayerLuck(player);
        if (luck == 0) luck = defaultLuck;

        // Get fishing loot breakdown
        Map<String, Double> baseBreakdown = plugin.getLootManager().getDefaultBiomeLoot(biomeKey);
        Map<String, Double> luckBreakdown = plugin.getLootManager().getBiomeLootWithLuck(biomeKey, luck);

        // Empty lines
        for (int i = 0; i < 6; i++) {
            player.sendMessage("");
        }

        // Main title
        player.sendMessage(ChatColor.GOLD + "\n================================\n"
                + ChatColor.BOLD + "Fishing Odds" 
                + ChatColor.RESET + ChatColor.GOLD + " - " + ChatColor.RESET + biomeDisplay
                + ChatColor.GOLD + "\n================================");

        // --- Fishing Loot Section ---
        player.sendMessage(ChatColor.GOLD + "--- Fishing Loot --- " + ChatColor.GREEN + "Luck: " + String.format("%.2f", luck) + "x" + ChatColor.GOLD + " ---");

        if (baseBreakdown.isEmpty() || luckBreakdown.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No loot for this area.");
        } else {
            for (String itemId : baseBreakdown.keySet()) {
                double baseOdds = baseBreakdown.getOrDefault(itemId, 0.0);
                double newOdds = luckBreakdown.getOrDefault(itemId, 0.0);

                ItemStack item = plugin.getItemManager().getCustomItem(itemId);
                int rarity = plugin.getItemManager().getItemRarity(item);
                String name;
                if (rarity < 8) {
                    name = item != null && item.hasItemMeta() ? item.getItemMeta().getDisplayName() : itemId;
                }
                else {
                    name = "???";
                }

                String display;
                if (Math.abs(luck - defaultLuck) < 1e-6) {
                    display = ChatColor.WHITE + formatPercent(newOdds);
                } else {
                    display = ChatColor.GRAY + formatPercent(baseOdds)
                            + ChatColor.WHITE + " -> " + formatPercent(newOdds);
                }

                player.sendMessage(ChatColor.AQUA + name + ChatColor.WHITE + " : " + display);
            }
        }

        // --- Boss Spawn Chances Section ---
        Double ruin = plugin.getPlayerManager().getTotalStats(player).getOrDefault("ruin", 1.0);
        Map<String, Double> defaultBossOdds = plugin.getLootManager().getBossOdds(biomeKey);
        Map<String, Double> modifiedBossOdds = plugin.getLootManager().getBossOdds(biomeKey, ruin);

        if (!modifiedBossOdds.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "================================");
            player.sendMessage(org.bukkit.ChatColor.GOLD + "--- Boss Chances --- " 
                + org.bukkit.ChatColor.RED + "Ruin: " + String.format("%.2f", ruin) + "x"
                + org.bukkit.ChatColor.GOLD + " ---");

            for (String bossId : defaultBossOdds.keySet()) {
                double defaultOdds = defaultBossOdds.getOrDefault(bossId, 0.0);
                double newOdds = modifiedBossOdds.getOrDefault(bossId, defaultOdds);

                String bossName = plugin.getBossManager().getBoss(bossId) != null
                        ? plugin.getBossManager().getBoss(bossId).getDisplayName()
                        : bossId;

                String oddsDisplay = Math.abs(newOdds - defaultOdds) < 1e-6
                        ? formatPercent(newOdds)
                        : ChatColor.GRAY + formatPercent(defaultOdds) + ChatColor.WHITE + " -> " + formatPercent(newOdds);

                // Main boss text
                net.md_5.bungee.api.chat.TextComponent bossText = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.RED + bossName + org.bukkit.ChatColor.WHITE + " : " + oddsDisplay
                );

                // Clickable [Click!] text
                net.md_5.bungee.api.chat.TextComponent clickText = new net.md_5.bungee.api.chat.TextComponent(
                        org.bukkit.ChatColor.BLUE + "" + org.bukkit.ChatColor.BOLD + " [Click!]"
                );
                clickText.setClickEvent(
                        new net.md_5.bungee.api.chat.ClickEvent(
                                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                "/odds boss " + bossId
                        )
                );
                clickText.setHoverEvent(
                        new net.md_5.bungee.api.chat.HoverEvent(
                                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                new net.md_5.bungee.api.chat.ComponentBuilder("View detailed odds for this boss").create()
                        )
                );

                // Combine main text + clickable
                bossText.addExtra(clickText);

                // Send to player
                player.spigot().sendMessage(bossText);
            }
        }

        player.sendMessage(ChatColor.GOLD + "================================\n");
        player.sendMessage("");

        return true;
    }


    // --- Helper to show a specific boss's drops ---
    private void showSpecificBossLoot(Player player, String bossId) {
        Boss boss = plugin.getBossManager().getBoss(bossId);
        if (boss == null) {
            player.sendMessage(ChatColor.RED + "Unknown boss ID: " + bossId);
            return;
        }

        List<BossLootEntry> drops = boss.getDrops();
        if (drops.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "This boss has no configured drops.");
            return;
        }


        player.sendMessage(ChatColor.GOLD + "\n================================\n"
                + ChatColor.BOLD + "Loot for Boss: " 
                + ChatColor.RED + boss.getDisplayName() 
                + ChatColor.GOLD + "\n================================");

        for (BossLootEntry entry : drops) {
            String amountDisplay = (entry.getMin() == entry.getMax()) ? 
                               String.valueOf(entry.getMin()) : 
                               entry.getMin() + "-" + entry.getMax();
            ItemStack item = plugin.getItemManager().getCustomItem(entry.getItemKey());
            String name = (item != null && item.hasItemMeta()) ? item.getItemMeta().getDisplayName() : entry.getItemKey();
            player.sendMessage(ChatColor.AQUA + name + ChatColor.WHITE + " : "
                + amountDisplay + " (" + formatPercent(entry.getChance()) + ")");
        }

        player.sendMessage(ChatColor.GOLD + "================================\n");
        player.sendMessage("");
    }

    //helper
    private String formatPercent(double value) {
        if (value == 0) return "0%";
        value = value * 100; // convert to percent

        int magnitude = (int) Math.floor(Math.log10(Math.abs(value))) + 1;

        // Compute scale to get at least 2 significant digits
        int scale = Math.max(0, 2 - magnitude);

        BigDecimal bd = new BigDecimal(value).setScale(scale, RoundingMode.HALF_UP);
        return bd.stripTrailingZeros().toPlainString() + "%";
    }


    private boolean starterCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        // Get the starter rod from your custom items
        ItemStack starterRod = plugin.getItemManager().getCustomItem("STARTER_ROD");
        if (starterRod == null) {
            player.sendMessage(ChatColor.RED + "Starter rod is not defined in custom_items.yml!");
            return true;
        }

        player.getInventory().addItem(starterRod.clone());
        player.sendMessage(ChatColor.AQUA + "You received your starter fishing rod!");

        return true;
    }


    private boolean discordCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        String discordLink = "https://discord.gg/qunuTHr9";
        player.sendMessage(ChatColor.AQUA + "Join our Discord: " + ChatColor.UNDERLINE + discordLink);

        return true;
    }

    private boolean fisherooCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            sender.sendMessage(ChatColor.YELLOW + "Reloading Fisheroo plugin...");

            // 1. Cancel all scheduled tasks
            plugin.getServer().getScheduler().cancelTasks(plugin);

            // 2. Clear old data from managers
            if (plugin.getItemManager() != null) plugin.getItemManager().clearCustomItems();
            if (plugin.getLootManager() != null) plugin.getLootManager().clearLootTables();
            if (plugin.getRecipeManager() != null) plugin.getRecipeManager().clearRecipes();
            if (plugin.getBiomeManager() != null) plugin.getBiomeManager().clearBiomes();
            if (plugin.getBossManager() != null) plugin.getBossManager().clearBosses();
            if (plugin.getSellManager() != null) plugin.getSellManager().clearItemValues();

            // 3. Reload plugin configuration
            plugin.reloadConfig();

            // 4. Load fresh data
            plugin.getItemManager().loadCustomItems();
            plugin.getLootManager().loadLootTables();
            plugin.getRecipeManager().loadRecipes();
            plugin.getBiomeManager().loadBiomes();
            plugin.getBossManager().loadBosses();
            plugin.getSellManager().loadItemValues();

              // 5. Recreate bossSpawner after bosses have loaded
            if (plugin.getBossManager() != null) {
                plugin.getBossManager().bossSpawner = new BossSpawner(plugin.getBossManager(), 20);
            }

            sender.sendMessage(ChatColor.GREEN + "Fisheroo configuration reloaded successfully!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /fisheroo reload");
        return true;
    }

    private boolean recipeCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }
        

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /recipe <item>");
            player.sendMessage(org.bukkit.ChatColor.YELLOW + "Are you sure you didn't mean " 
                + org.bukkit.ChatColor.AQUA + "/recipes" + org.bukkit.ChatColor.YELLOW + " to see all craftable items?");
            return true;
        }

        String itemKey = args[0];

        // Open the recipe GUI from RecipeManager
        plugin.getRecipeManager().openRecipeGUI(player, itemKey);

        return true;
    }

    private List<String> recipeTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return plugin.getItemManager().getCustomItems().keySet().stream()
                    .map(String::toLowerCase)
                    .filter(key -> key.startsWith(partial))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }

    private boolean allRecipesCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Only players can use this command!");
            return true;
        }

        plugin.getRecipeManager().openAllRecipesGUI(player);
        return true;
    }

    private boolean sellCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        // Open the sell GUI for this player
        plugin.getSellManager().openSellGUI(player);

        return true;
    }


    private boolean giveSkullCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /giveskull <skullId>");
            return true;
        }

        int skullId = Integer.parseInt(args[0]);
        ItemStack skull = SkullUtil.getSkullById(skullId); // assumes SkullUtil has a getSkull(String id) method
        if (skull == null) {
            player.sendMessage(ChatColor.RED + "Invalid skull ID: " + skullId);
            return true;
        }

        player.getInventory().addItem(skull);
        player.sendMessage(ChatColor.GREEN + "Gave skull: " + skullId);
        return true;
    }

    private boolean minBossRarityCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /minbossrarity <get|set|reset>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "get" -> {
                sender.sendMessage(ChatColor.GREEN + "Current min boss rarity: " + plugin.getPlayerManager().getMinBossRarity(player));
            }
            case "reset" -> {
                plugin.getPlayerManager().setMinBossRarity(player, 0);
                sender.sendMessage(ChatColor.GREEN + "Min boss rarity has been reset to 0");
            }
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /minbossrarity set <number>");
                    return true;
                }
                try {
                    int value = Integer.parseInt(args[1]);
                    plugin.getPlayerManager().setMinBossRarity(player, value);
                    sender.sendMessage(ChatColor.GREEN + "Min boss rarity set to " + value);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid number: " + args[1]);
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + subcommand);
        }

        return true;
    }
    
    private List<String> minBossRarityTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("get", "set", "reset").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }


    private boolean customWarpCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /customwarp <warpName>");
            return true;
        }

        String warpName = args[0];

        // case-insensitive lookup from warps.yml
        FileConfiguration warpsConfig = plugin.getWarpManager().getWarpsConfig();
        if (!warpsConfig.contains("warps")) {
            player.sendMessage(ChatColor.RED + "No warps are defined!");
            return true;
        }

        String matchedKey = null;
        for (String key : warpsConfig.getConfigurationSection("warps").getKeys(false)) {
            if (key.equalsIgnoreCase(warpName)) {
                matchedKey = key;
                break;
            }
        }

        if (matchedKey == null) {
            player.sendMessage(ChatColor.RED + "Warp not found: " + warpName);
            return true;
        }

        boolean success = plugin.getWarpManager().warpPlayer(player, matchedKey);
        if (!success) {
            player.sendMessage(ChatColor.RED + "Warp failed: " + matchedKey);
        }
        return true;
    }

    private List<String> customWarpTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            FileConfiguration warpsConfig = plugin.getWarpManager().getWarpsConfig();
            if (!warpsConfig.contains("warps")) return Collections.emptyList();

            String partial = args[0].toLowerCase();
            return warpsConfig.getConfigurationSection("warps").getKeys(false).stream()
                    .map(String::toLowerCase) // <-- convert to lowercase
                    .filter(k -> k.startsWith(partial))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }

    private boolean spawnCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        boolean success = plugin.getWarpManager().warpPlayer(player, "SPAWN"); // assumes warp key is "SPAWN"
        if (!success) {
            player.sendMessage(ChatColor.RED + "Spawn warp is not set!");
        }

        return true;
    }

    private boolean beachCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        boolean success = plugin.getWarpManager().warpPlayer(player, "BEACH"); 
        if (!success) {
            player.sendMessage(ChatColor.RED + "This warp is not set!");
        }

        return true;
    }

    private boolean setGlobalBonusCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setglobalbonus <stat> <value>");
            return true;
        }

        String stat = args[0].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[1]);
            return true;
        }

        plugin.getPlayerManager().setGlobalBonus(stat, value);
        sender.sendMessage(ChatColor.GREEN + "Global bonus for " + stat + " set to " + value);
        return true;
    }

    private boolean setGlobalMultiplierCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /setglobalmultiplier <stat> <value>");
            return true;
        }

        String stat = args[0].toLowerCase();
        double value;
        try {
            value = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Invalid number: " + args[1]);
            return true;
        }

        plugin.getPlayerManager().setGlobalMultiplier(stat, value);
        sender.sendMessage(ChatColor.GREEN + "Global multiplier for " + stat + " set to " + value);
        return true;
    }

    private List<String> setGlobalTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return validStats.stream()
                    .filter(stat -> stat.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }


    private boolean clearGlobalModifiers(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender.isOp() || sender.hasPermission("fisheroo.admin"))) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        plugin.getPlayerManager().clearGlobalModifiers();
        sender.sendMessage(ChatColor.GREEN + "All global modifiers reset.");
        return true;
    }

    private boolean customWeatherCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run this command.");
            return true;
        }

        if (!(player.isOp() || player.hasPermission("fisheroo.admin"))) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /customweather <weather>");
            player.sendMessage(ChatColor.YELLOW + "Available weathers: " + listWeathers());
            return true;
        }

        String input = args[0].toUpperCase();

        try {
            WeatherManager.WeatherType type = WeatherManager.WeatherType.valueOf(input);
            plugin.getWeatherManager().applyWeather(type);
            player.sendMessage(ChatColor.GREEN + "Weather set to " + ChatColor.AQUA + type.name());
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown weather: " + input);
            player.sendMessage(ChatColor.YELLOW + "Available weathers: " + listWeathers());
        }

        return true;
    }

    // --- Helper method ---
    private String listWeathers() {
        StringBuilder sb = new StringBuilder();
        for (WeatherManager.WeatherType type : WeatherManager.WeatherType.values()) {
            sb.append(type.name()).append(", ");
        }
        return sb.substring(0, sb.length() - 2);
    }
    




    public CommandExecutor getCustomFishCommand() { return this::customFishCommand; }
    public CommandExecutor getSetStatCommand() { return this::setStatCommand; }
    public TabCompleter getSetStatTabCompleter() { return this::setStatTabComplete; }
    public CommandExecutor getUpdateItemsCommand() { return this::updateItemsCommand; }
    public CommandExecutor getGiveItemCommand() { return this::giveItemCommand; }
    public TabCompleter getGiveItemTabCompleter() {return this::giveItemTabComplete; }
    public CommandExecutor getSummonBossCommand() { return this::summonBossCommand; }
    public TabCompleter getSummonBossTabCompleter() { return this::summonBossTabComplete; }
    public CommandExecutor getLootBreakdownCommand() { return this::lootBreakdownCommand; }
    public CommandExecutor getStarterCommand() { return this::starterCommand; }
    public CommandExecutor getDiscordCommand() { return this::discordCommand; }
    public CommandExecutor getFisherooCommand() { return this::fisherooCommand; }
    public CommandExecutor getRecipeCommand() { return this::recipeCommand; }
    public TabCompleter getRecipeTabCompleter() { return this::recipeTabComplete; }
    public CommandExecutor getAllRecipesCommand() { return this::allRecipesCommand; }
    public CommandExecutor getSellCommand() { return this::sellCommand; }
    public CommandExecutor getGiveSkullCommand() { return this::giveSkullCommand; }
    public CommandExecutor getMinBossRarityCommand() { return this::minBossRarityCommand; }
    public TabCompleter getMinBossRarityTabCompleter() { return this::minBossRarityTabComplete; }
    public CommandExecutor getCustomWarpCommand() { return this::customWarpCommand; }
    public TabCompleter getCustomWarpTabCompleter() { return this::customWarpTabComplete; }
    public CommandExecutor getSpawnCommand() { return this::spawnCommand; }
    public CommandExecutor getBeachCommand() { return this::beachCommand; }
    public CommandExecutor getSetGlobalBonusCommand() { return this::setGlobalBonusCommand; }
    public CommandExecutor getSetGlobalMultiplierCommand() { return this::setGlobalMultiplierCommand; }
    public TabCompleter getSetGlobalTabComplete() { return this::setGlobalTabComplete; }
    public CommandExecutor getClearGlobalModifiersCommand() { return this::clearGlobalModifiers; }
    public CommandExecutor getCustomWeatherCommand() { return this::customWeatherCommand; }
    


}
