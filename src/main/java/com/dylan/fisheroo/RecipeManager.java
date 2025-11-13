package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Listener;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class RecipeManager implements Listener {

    private final Main plugin;

    // Recipe results map
    private Map<NamespacedKey, ItemStack> recipeResults = new HashMap<>();

    public RecipeManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) plugin.saveResource("recipes.yml", false);

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (!config.isConfigurationSection("recipes")) return;

        for (String recipeKey : config.getConfigurationSection("recipes").getKeys(false)) {
            String type = config.getString("recipes." + recipeKey + ".type");
            String resultKey = config.getString("recipes." + recipeKey + ".result");

            // Get the result item from custom items
            ItemStack resultItem = plugin.getItemManager().getCustomItem(resultKey);
            if (resultItem == null) continue;

            NamespacedKey nsKey = new NamespacedKey(plugin, recipeKey);
            recipeResults.put(nsKey, resultItem.clone());

            if ("SHAPED".equalsIgnoreCase(type)) {
                List<String> shape = config.getStringList("recipes." + recipeKey + ".shape");
                ShapedRecipe recipe = new ShapedRecipe(nsKey, resultItem.clone());
                recipe.shape(shape.toArray(new String[0]));

                if (config.isConfigurationSection("recipes." + recipeKey + ".ingredients")) {
                    for (String k : config.getConfigurationSection("recipes." + recipeKey + ".ingredients").getKeys(false)) {
                        String value = config.getString("recipes." + recipeKey + ".ingredients." + k);
                        ItemStack ing = plugin.getItemManager().getCustomItem(value);
                        if (ing == null) ing = new ItemStack(Material.matchMaterial(value));
                        if (ing != null) {
                            recipe.setIngredient(k.charAt(0), new RecipeChoice.ExactChoice(ing.clone()));
                        }
                    }
                }

                Bukkit.addRecipe(recipe);

                // Give the recipe to all online players so it highlights green if craftable
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.discoverRecipe(nsKey);
                }
            }
            // Optional: handle SHAPELESS recipes similarly if needed
        }
    }


    public void giveAllRecipesToPlayer(Player player) {
        if (player == null) return;
        for (NamespacedKey key : recipeResults.keySet()) {
            player.discoverRecipe(key);
        }
    }

    public void resetRecipesForPlayer(Player player) {
        // Remove all recipes the player knows
        for (NamespacedKey key : player.getDiscoveredRecipes()) {
            player.undiscoverRecipe(key);
        }
    }

    // Open the recipe GUI using dispenser (3x3)
    public void openRecipeGUI(Player player, String itemKey) {
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) {
            player.sendMessage("§cNo recipes.yml found!");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Find the recipe key case-insensitively
        String foundKey = null;
        for (String recipeKey : config.getConfigurationSection("recipes").getKeys(false)) {
            String resultKey = config.getString("recipes." + recipeKey + ".result");
            if (resultKey != null && resultKey.equalsIgnoreCase(itemKey)) {
                foundKey = recipeKey;
                break;
            }
        }

        if (foundKey == null) {
            player.sendMessage("§cRecipe not found for: " + itemKey);
            return;
        }

        // Get shape and ingredients
        List<String> shape = config.getStringList("recipes." + foundKey + ".shape");
        Map<Character, String> ingredientsMap = new HashMap<>();
        if (config.isConfigurationSection("recipes." + foundKey + ".ingredients")) {
            for (String k : config.getConfigurationSection("recipes." + foundKey + ".ingredients").getKeys(false)) {
                ingredientsMap.put(k.charAt(0), config.getString("recipes." + foundKey + ".ingredients." + k));
            }
        }

        // Create 3x3 inventory with title in all caps
        Inventory gui = Bukkit.createInventory(null, InventoryType.DISPENSER, "RECIPE: " + itemKey.toUpperCase());

        int index = 0;
        for (int row = 0; row < 3; row++) {
            String line = row < shape.size() ? shape.get(row) : "   "; // fill empty row if missing
            for (int col = 0; col < 3; col++) {
                char c = col < line.length() ? line.charAt(col) : ' '; // default empty char
                String ingKey = ingredientsMap.get(c);

                ItemStack item;
                if (ingKey == null || ingKey.equals(" ")) {
                    item = new ItemStack(Material.AIR); // empty slot
                } else {
                    item = plugin.getItemManager().getCustomItem(ingKey);
                    if (item == null) {
                        Material mat = Material.matchMaterial(ingKey);
                        if (mat != null) item = new ItemStack(mat);
                    }

                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            // set item_id to 0 for display purposes
                            NamespacedKey idKey = new NamespacedKey(plugin, "item_id");
                            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, "0");

                            // mark as display-only
                            NamespacedKey displayKey = new NamespacedKey(plugin, "display_only");
                            meta.getPersistentDataContainer().set(displayKey, PersistentDataType.BYTE, (byte)1);

                            item.setItemMeta(meta);
                        }
                    }
                }

                gui.setItem(index, item);
                index++;
            }
        }

        player.openInventory(gui);
    }

    // Prevent players from taking items out
   @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("RECIPE: ")) {
            event.setCancelled(true); // Prevent taking items
        }
    }

    public java.util.List<String> getAllCraftableItems() {
        java.util.List<String> items = new java.util.ArrayList<>();
        File file = new File(plugin.getDataFolder(), "recipes.yml");
        if (!file.exists()) return items;

        org.bukkit.configuration.file.FileConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("recipes")) return items;

        for (String recipeKey : config.getConfigurationSection("recipes").getKeys(false)) {
            String resultKey = config.getString("recipes." + recipeKey + ".result");
            if (resultKey != null) items.add(resultKey);
        }
        return items;
    }

    public void openAllRecipesGUI(org.bukkit.entity.Player player) {
        java.util.List<String> allItems = getAllCraftableItems(); // returns item IDs
        if (allItems.isEmpty()) {
            player.sendMessage(org.bukkit.ChatColor.RED + "No craftable items found!");
            return;
        }

        int size = ((allItems.size() - 1) / 9 + 1) * 9;
        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, size, org.bukkit.ChatColor.GOLD + "Craftable Recipes");

        for (String itemId : allItems) {
            org.bukkit.inventory.ItemStack item = plugin.getItemManager().getCustomItem(itemId);
            if (item == null) continue;

            // Add lore to show click hint
            org.bukkit.inventory.ItemStack displayItem = item.clone();
            org.bukkit.inventory.meta.ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
                lore.add(org.bukkit.ChatColor.BLUE + "Click to view crafting recipe");
                meta.setLore(lore);

                // Store the item ID in PersistentDataContainer so we can retrieve it on click
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "recipe_id");
                meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, itemId);

                displayItem.setItemMeta(meta);
            }

            gui.addItem(displayItem);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onAllRecipesGUIClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;

        org.bukkit.inventory.InventoryView view = event.getView();
        if (!org.bukkit.ChatColor.stripColor(view.getTitle()).equalsIgnoreCase("Craftable Recipes")) return;

        event.setCancelled(true); // prevent taking items

        org.bukkit.inventory.ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        // Read the item_id from PersistentDataContainer
        NamespacedKey itemIDkey = new NamespacedKey(plugin, "item_id");
        String itemId = meta.getPersistentDataContainer().get(itemIDkey, org.bukkit.persistence.PersistentDataType.STRING);
        String yamlID = plugin.getItemManager().getYamlKeyFromId(itemId);
        if (yamlID == null) return; // safety check

        // Open the 3x3 crafting recipe GUI
        openRecipeGUI(player, yamlID);
    }


    @org.bukkit.event.EventHandler
    public void onRecipeGUIClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof org.bukkit.entity.Player player)) return;

        org.bukkit.inventory.InventoryView view = event.getView(); // <-- use getView()
        String title = org.bukkit.ChatColor.stripColor(view.getTitle());
        
        // Detect if it's a single item recipe GUI
        if (!title.startsWith("RECIPE: ")) return;

        // Schedule to reopen the big recipes GUI after 1 tick
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                openAllRecipesGUI(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    public void clearRecipes() {
        // Remove all currently registered Bukkit recipes
        for (NamespacedKey key : recipeResults.keySet()) {
            Bukkit.removeRecipe(key);
        }
        recipeResults.clear(); // clear cached recipe results
    }

}
