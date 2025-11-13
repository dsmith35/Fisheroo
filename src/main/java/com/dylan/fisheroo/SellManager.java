package com.dylan.fisheroo;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

public class SellManager implements Listener {

    private final Main plugin;
    private final Map<String, Double> itemValues = new HashMap<>();

    public SellManager(Main plugin) {
        this.plugin = plugin;
        loadItemValues();
    }

    public void loadItemValues() {
        File file = new File(plugin.getDataFolder(), "item_values.yml");
        if (!file.exists()) {
            plugin.saveResource("item_values.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.isConfigurationSection("item_values")) {
            for (String key : config.getConfigurationSection("item_values").getKeys(false)) {
                double value = config.getDouble("item_values." + key);
                itemValues.put(key.toUpperCase(), value); // store by YAML key
            }
        }
    }

    public double getSellPrice(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String itemId = meta.getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (itemId == null) return 0.0;

        // Convert custom item_id to YAML key
        String yamlKey = plugin.getItemManager().getYamlKeyFromId(itemId);
        if (yamlKey == null) return 0.0;

        // Lookup value using YAML key
        return itemValues.getOrDefault(yamlKey.toUpperCase(), 0.0);
    }



    public void openSellGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Sell Items");

        // Initialize Sell button
        updateSellButton(gui);

        player.openInventory(gui);
    }

    private void updateSellButton(Inventory gui) {
        double total = 0;

        // Calculate total value of all sellable items except last slot
        for (int i = 0; i < gui.getSize() - 1; i++) {
            ItemStack item = gui.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            double value = getSellPrice(item);
            if (value > 0) {
                total += value * item.getAmount();
            }
        }

        // Create emerald block button
        ItemStack sellButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = sellButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Click to Sell! Total: " + total + " coins");
            sellButton.setItemMeta(meta);
        }

        gui.setItem(53, sellButton);
    }

    @EventHandler
    public void onSellGUIClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!ChatColor.stripColor(event.getView().getTitle()).equalsIgnoreCase("Sell Items")) return;

        int slot = event.getRawSlot();

        // Prevent moving the sell button
        if (slot == 53) {
            event.setCancelled(true);

            Inventory inv = event.getInventory();
            double total = 0;

            for (int i = 0; i < inv.getSize() - 1; i++) {
                ItemStack item = inv.getItem(i);
                if (item == null || item.getType() == Material.AIR) continue;

                double value = getSellPrice(item);
                if (value > 0) {
                    total += value * item.getAmount();
                    inv.setItem(i, null); // remove sold item
                }
            }

            if (total > 0) {
                giveCurrency(player, total);
                player.sendMessage(ChatColor.GREEN + "Sold items for " + total + " coins!");
            } else {
                player.sendMessage(ChatColor.RED + "No sellable items!");
            }

            // Update emerald block after selling
            updateSellButton(inv);
            return;
        }

        // Update emerald block if player moves or removes items
        Bukkit.getScheduler().runTask(plugin, () -> updateSellButton(event.getInventory()));
    }


    // Converts a numeric total into Gold Currency
    private void giveCurrency(Player player, double total) {
        if (total <= 0) return;

        int remaining = (int) total;

        // Royal Gold Block = 64 Gold Blocks = 262,144 coins
        int royalBlocks = remaining / 262144;
        remaining %= 262144;

        int blocks = remaining / 4096; // 64 bars per block ? 64 * 64 = 4096 coins
        remaining %= 4096;

        int bars = remaining / 64;
        int coins = remaining % 64;

        ItemStack royalBlockItem = plugin.getItemManager().getCustomItem("ROYAL_GOLD_BLOCK");
        ItemStack blockItem = plugin.getItemManager().getCustomItem("GOLD_BLOCK");
        ItemStack barItem = plugin.getItemManager().getCustomItem("GOLD_BAR");
        ItemStack coinItem = plugin.getItemManager().getCustomItem("GOLD_COIN");

        if (royalBlocks > 0 && royalBlockItem != null) {
            royalBlockItem.setAmount(royalBlocks);
            player.getInventory().addItem(royalBlockItem);
        }

        if (blocks > 0 && blockItem != null) {
            blockItem.setAmount(blocks);
            player.getInventory().addItem(blockItem);
        }

        if (bars > 0 && barItem != null) {
            barItem.setAmount(bars);
            player.getInventory().addItem(barItem);
        }

        if (coins > 0 && coinItem != null) {
            coinItem.setAmount(coins);
            player.getInventory().addItem(coinItem);
        }
    }

    @EventHandler
    public void onSellGUIClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (!ChatColor.stripColor(event.getView().getTitle()).equalsIgnoreCase("Sell Items")) return;

        Inventory inv = event.getInventory();

        // Return unsold items
        for (int i = 0; i < inv.getSize() - 1; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                player.getInventory().addItem(item);
            }
        }
    }

    public void clearItemValues() {
        itemValues.clear(); // remove all loaded item values
    }
}
