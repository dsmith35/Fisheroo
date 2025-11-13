package com.dylan.fisheroo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import com.dylan.fisheroo.util.SkullUtil;

import ca.tweetzy.skulls.Skulls;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import com.dylan.fisheroo.util.SkullUtil;
import ca.tweetzy.skulls.api.interfaces.Skull;

import java.io.File;
import java.util.*;

public class ItemManager implements Listener {

    private final Main plugin;
    private boolean hasSkulls;
    private Map<String, ItemStack> customItems = new HashMap<>();
    private Map<String, ItemStack> customItemsById = new HashMap<>();
    private Map<String, String> yamlKeyById = new HashMap<>();
    private FileConfiguration customItemsConfig;
    private File customItemsFile;
    private FileConfiguration armorSetsConfig;
    private File armorSetsFile;
    private FileConfiguration fishingSpeedsConfig;
    private File fishingSpeedsFile;

    public ItemManager(Main plugin, boolean hasSkulls) {
        this.plugin = plugin;

        this.hasSkulls = hasSkulls;

        // Load custom_items.yml
        customItemsFile = new File(plugin.getDataFolder(), "custom_items.yml");
        if (!customItemsFile.exists()) plugin.saveResource("custom_items.yml", false);
        customItemsConfig = YamlConfiguration.loadConfiguration(customItemsFile);

        // Load armor_sets.yml
        armorSetsFile = new File(plugin.getDataFolder(), "armor_sets.yml");
        if (!armorSetsFile.exists()) plugin.saveResource("armor_sets.yml", false);
        armorSetsConfig = YamlConfiguration.loadConfiguration(armorSetsFile);

        // Load fishing_speeds.yml
        fishingSpeedsFile = new File(plugin.getDataFolder(), "fishing_speeds.yml");
        if (!fishingSpeedsFile.exists()) plugin.saveResource("fishing_speeds.yml", false);
        fishingSpeedsConfig = YamlConfiguration.loadConfiguration(fishingSpeedsFile);
        
    }

    public static class RarityInfo {
        ChatColor color;
        String label;

        RarityInfo(ChatColor color, String label) {
            this.color = color;
            this.label = label;
        }
    }

    private static final Map<Integer, RarityInfo> RARITY_MAP = Map.of(
        0, new RarityInfo(ChatColor.WHITE, "&f&lCOMMON"),
        1, new RarityInfo(ChatColor.DARK_GREEN, "&2&lUNCOMMON"),
        2, new RarityInfo(ChatColor.BLUE, "&9&lRARE"),
        3, new RarityInfo(ChatColor.DARK_PURPLE, "&5&lEPIC"),
        4, new RarityInfo(ChatColor.GOLD, "&6&lLEGENDARY"),
        5, new RarityInfo(ChatColor.RED, "&c&lMYTHIC"),
        6, new RarityInfo(ChatColor.YELLOW, "&e&lDIVINE"),
        7, new RarityInfo(ChatColor.DARK_RED, "&4&lTRANSCENDENT"),
        8, new RarityInfo(ChatColor.LIGHT_PURPLE, "&c&lS&6&lU&e&lP&a&lR&b&lE&9&lM&d&lE"),

        -1, new RarityInfo(ChatColor.DARK_GRAY, "&8&lMONSTER DROP")
    );

    private static final ChatColor[] RAINBOW_COLORS = {
        ChatColor.RED,
        ChatColor.GOLD,
        ChatColor.YELLOW,
        ChatColor.GREEN,
        ChatColor.AQUA,
        ChatColor.BLUE,
        ChatColor.LIGHT_PURPLE,
    };


    public void loadCustomItems() {
        if (!customItemsConfig.isConfigurationSection("items")) return;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id"); // Hidden ID key
        Set<String> seenIds = new HashSet<>(); // track used IDs
        List<String> duplicateIds = new ArrayList<>();

        for (String key : customItemsConfig.getConfigurationSection("items").getKeys(false)) {
            ItemStack item;

            if (hasSkulls && customItemsConfig.contains("items." + key + ".skull")) {
                int skullId = customItemsConfig.getInt("items." + key + ".skull", 0);
                ItemStack skullItem = SkullUtil.getSkullById(skullId);
                if (skullItem != null) {
                    item = skullItem;
                } else {
                    plugin.getLogger().warning("Failed to create skull for item " + key + " (skull ID " + skullId + "), using default material.");
                    String matName = customItemsConfig.getString("items." + key + ".material", "SKELETON_SKULL"); // fallback default
                    Material mat = Material.matchMaterial(matName);
                    if (mat == null) mat = Material.STONE; // final safety
                    item = new ItemStack(mat);
                }
            } else {
                // Regular material
                String matName = customItemsConfig.getString("items." + key + ".material");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().warning("Invalid material for item " + key + ", using STONE.");
                    mat = Material.STONE;
                }
                item = new ItemStack(mat);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {

                // --- Lore ---
                List<String> lore = new ArrayList<>(customItemsConfig.getStringList("items." + key + ".lore"));
                lore.replaceAll(s -> ChatColor.GRAY + ChatColor.translateAlternateColorCodes('&', s));
                
                // --- Stats ---
                if (customItemsConfig.isConfigurationSection("items." + key + ".stats")) {
                    // --- Empty Line ---
                    lore.add("");

                    String type = customItemsConfig.getString("items." + key + ".type", "");
                    if ("artifact".equalsIgnoreCase(type)) {
                        lore.add(ChatColor.DARK_BLUE + "When in inventory:");
                    }
                    else if ("talisman".equalsIgnoreCase(type)) {
                        lore.add(ChatColor.DARK_BLUE + "When in offhand:");
                    }
                    else if (item.getType() == Material.FISHING_ROD) {
                        lore.add(ChatColor.DARK_BLUE + "When in mainhand:");
                    }

                    // Damage
                    double damage = customItemsConfig.getDouble("items." + key + ".stats.damage", 0.0);
                    if (damage != 0.0) {
                        // Add lore
                        lore.add(ChatColor.BLUE + "Damage: " + (int)damage);

                        // Add Attribute Modifier for latest API
                        AttributeModifier modifier = new AttributeModifier(
                                UUID.randomUUID(),
                                "custom_damage",
                                damage,
                                AttributeModifier.Operation.ADD_NUMBER
                        );

                        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
                    }

                    if (item.getType() == Material.FISHING_ROD) {
                        // --- Fishing Speed ---
                        int fishingSpeed = customItemsConfig.getInt("items." + key + ".stats.fishing_speed", 0);
                        String speedKey = String.valueOf(fishingSpeed);

                        // Look up the name in fishing_speeds.yml
                        String speedName = fishingSpeedsConfig.getString("speeds." + speedKey + ".name", "Normal").toUpperCase();

                        // Add lore line
                        lore.add(ChatColor.AQUA + "Fishing Speed: " + speedName);

                    } 


                    // Luck
                    double luck = customItemsConfig.getDouble("items." + key + ".stats.luck", 0.0);
                    if (luck != 0.0) {
                        String luckSign = luck > 0 ? "+" : ""; // only add '+' if positive
                        lore.add(ChatColor.GREEN + "Luck: " + luckSign + ((int)(luck * 100)) + "%");
                    }

                    // Ruin
                    double ruin = customItemsConfig.getDouble("items." + key + ".stats.ruin", 0.0);
                    if (ruin != 0.0) {
                        String ruinSign = ruin > 0 ? "+" : ""; // only add '+' if positive
                        lore.add(ChatColor.RED + "Ruin: " + ruinSign + ((int)(ruin * 100)) + "%");
                    }

                    // Multifish
                    int multifish = customItemsConfig.getInt("items." + key + ".stats.multifish", 0);
                    if (multifish != 0) {
                            lore.add(ChatColor.LIGHT_PURPLE + "Multifish: +" + (int)(multifish));
                        }
                }

                // --- Empty Line ---
                lore.add("");

                // --- Insert Set Line (if present) ---
                String setId = customItemsConfig.getString("items." + key + ".set");
                if (armorSetsConfig.isConfigurationSection("armor_sets." + setId)) {
                    // set name
                    String setName = armorSetsConfig.getString("armor_sets." + setId + ".name", setId.toUpperCase());
                    setName = ChatColor.translateAlternateColorCodes('&', setName);
                    if (setName.length() > 1 && setName.charAt(0) == ChatColor.COLOR_CHAR) {
                        setName = setName.substring(0, 2) + ChatColor.BOLD + setName.substring(2);
                    }
                    else {
                        setName = ChatColor.BOLD + setName;
                    }
                    lore.add(lore.size(), ChatColor.translateAlternateColorCodes('&', setName.toUpperCase() + " SET"));

                    // set bonus
                    double setBonusLuck = armorSetsConfig.getDouble("armor_sets." + setId + ".stats.luck", 0.0);
                    double setBonusRuin = armorSetsConfig.getDouble("armor_sets." + setId + ".stats.ruin", 0.0);
                    double setBonusMultifish = armorSetsConfig.getDouble("armor_sets." + setId + ".stats.multifish", 0.0);
                    String setBonusInfo = ChatColor.WHITE + "Set Bonus: "
                    + ChatColor.GREEN + (setBonusLuck != 0 ? (setBonusLuck > 0 ? "+" : "") + ((int)(setBonusLuck*100)) + "% Luck " : "")
                    + ChatColor.RED + (setBonusRuin != 0 ? (setBonusRuin > 0 ? "+" : "") + ((int)(setBonusRuin*100)) + "% Ruin" : "")
                    + ChatColor.LIGHT_PURPLE + (setBonusMultifish != 0 ? (setBonusMultifish > 0 ? "+" : "") + ((int)(setBonusMultifish)) + " Multifish" : "");
                    lore.add(setBonusInfo);

                    // --- Empty Line ---
                    lore.add("");
                }


                // --- Rarity & Name ---
                int rarity = customItemsConfig.getInt("items." + key + ".rarity", 0);
                String baseName = customItemsConfig.getString("items." + key + ".name", key);

                // Translate & codes
                baseName = ChatColor.translateAlternateColorCodes('&', baseName);

                // Check if name starts with a color code
                String nameColor = ChatColor.getLastColors(baseName);
                if (nameColor.isEmpty()) {
                    // No color specified in name, use rarity color
                    RarityInfo rarityInfo = RARITY_MAP.getOrDefault(rarity, new RarityInfo(ChatColor.WHITE, "&f&lCOMMON"));
                    nameColor = rarityInfo.color.toString();
                }
                String strippedName = ChatColor.stripColor(baseName);
                if (rarity < 8) {
                    // Strip existing colors for base name text
                    meta.setDisplayName(nameColor + strippedName);
                }
                else {
                    meta.setDisplayName(getStaticRainbow(strippedName));
                }

                // Add rarity label
                if (customItemsConfig.contains("items." + key + ".rarity")) {
                    RarityInfo rarityInfo = RARITY_MAP.getOrDefault(rarity, new RarityInfo(ChatColor.WHITE, "&f&lCOMMON"));
                    lore.add(ChatColor.translateAlternateColorCodes('&', rarityInfo.label));
                }

                // -- set lore --

                meta.setLore(lore);

                // --- Unbreakable ---
                if (customItemsConfig.getBoolean("items." + key + ".unbreakable", false)) {
                    meta.setUnbreakable(true);
                }

                // --- Glow + hide everything ---
                if (customItemsConfig.getBoolean("items." + key + ".glow", false)) {
                    // Add a fake enchantment to make it glow
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.AQUA_AFFINITY, 1, true);

                    // Hide enchantment and attributes from tooltip
                    meta.addItemFlags(
                        org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                        org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                        org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE,
                        org.bukkit.inventory.ItemFlag.HIDE_DESTROYS,
                        org.bukkit.inventory.ItemFlag.HIDE_PLACED_ON
                    );
                }

                // --- Enchantments ---
                if (customItemsConfig.isConfigurationSection("items." + key + ".enchantments")) {
                    for (String enchKey : customItemsConfig.getConfigurationSection("items." + key + ".enchantments").getKeys(false)) {
                        try {
                            int level = customItemsConfig.getInt("items." + key + ".enchantments." + enchKey);
                            org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByName(enchKey);
                            if (ench != null) meta.addEnchant(ench, level, true);
                        } catch (Exception ignored) {}
                    }
                }

                // --- Item Flags ---
                if (customItemsConfig.isList("items." + key + ".flags")) {
                    for (String flagStr : customItemsConfig.getStringList("items." + key + ".flags")) {
                        try {
                            meta.addItemFlags(org.bukkit.inventory.ItemFlag.valueOf(flagStr));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }

                // --- Leather Armor Color ---
                if (meta instanceof LeatherArmorMeta leatherMeta) {
                    String colorHex = customItemsConfig.getString("items." + key + ".color");
                    if (colorHex != null) {
                        try {
                            Color color = Color.fromRGB(
                                    Integer.valueOf(colorHex.substring(1, 3), 16),
                                    Integer.valueOf(colorHex.substring(3, 5), 16),
                                    Integer.valueOf(colorHex.substring(5, 7), 16)
                            );
                            leatherMeta.setColor(color);
                            meta = leatherMeta;
                        } catch (Exception ignored) {}
                    }
                }

                // --- Hidden ID from YAML ---
                String itemId = customItemsConfig.getString("items." + key + ".id", key);

                 // check for duplicates
                if (!seenIds.add(itemId)) {
                    duplicateIds.add(itemId);
                    continue; // skip registering duplicate
                }

                meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, itemId);
                item.setItemMeta(meta);

                // --- Add to maps ---
                customItems.put(key, item);          // YAML section name
                customItemsById.put(itemId, item);   // Custom item_id
                yamlKeyById.put(itemId, key);
            }
        }
        // If duplicates found, throw and stop plugin
        if (!duplicateIds.isEmpty()) {
            plugin.getLogger().severe("Duplicate item IDs found in custom_items.yml: " + duplicateIds);
            throw new IllegalStateException("Duplicate item IDs in custom_items.yml, plugin cannot start!");
        }
    }

    public ItemStack getCustomItem(String key) {
        return customItems.get(key) != null ? customItems.get(key).clone() : null; // gets custom item by yaml
    }

    public ItemStack getCustomItemById(String id) {
        return customItemsById.get(id) != null ? customItemsById.get(id).clone() : null;
    }

    public String getYamlKeyFromId(String id) {
        return yamlKeyById.get(id); // returns the YAML key (like "PIRATE_ROD") or null
    }

    public String getItemIdFromItem(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        return item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
    }

    // Update player items
    public int updatePlayerItems(Player player) {
        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        int replacedCount = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) continue;

            String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
            if (id != null && customItemsById.containsKey(id)) {
                ItemStack newItem = customItemsById.get(id);

                if (!areItemsEqual(item, newItem)) {
                    ItemStack replacement = newItem.clone();
                    replacement.setAmount(item.getAmount());
                    player.getInventory().setItem(i, replacement);
                    replacedCount += item.getAmount();
                }
            }
        }

        plugin.getPlayerManager().updateStats(player);
        return replacedCount;
    }

    private boolean areItemsEqual(ItemStack oldItem, ItemStack newItem) {
        if (oldItem.getType() != newItem.getType()) return false;
        ItemMeta oldMeta = oldItem.getItemMeta();
        ItemMeta newMeta = newItem.getItemMeta();
        if (oldMeta == null && newMeta == null) return true;
        if (oldMeta == null || newMeta == null) return false;
        return oldMeta.equals(newMeta);
    }


    public double getLuckFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 0.0;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 0.0;

        FileConfiguration cfg = getCustomItemsConfig();
        return cfg.getDouble("items." + yamlKey + ".stats.luck", 0.0);
    }

    public double getRuinFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 0.0;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 0.0;

        FileConfiguration cfg = getCustomItemsConfig();
        return cfg.getDouble("items." + yamlKey + ".stats.ruin", 0.0);
    }

    public double getMultifishFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 0.0;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 0.0;

        FileConfiguration cfg = getCustomItemsConfig();

        return cfg.getDouble("items." + yamlKey + ".stats.multifish", 0.0);
    }

      public int getItemRarity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 0;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 0;

        FileConfiguration cfg = getCustomItemsConfig();

        return cfg.getInt("items." + yamlKey + ".rarity", 0);
    }


    // --- Get fishing speed from item ---
    public int getFishingSpeedFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0; // default Normal

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 0;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 0;

        FileConfiguration cfg = getCustomItemsConfig();
        int fishingSpeed = cfg.getInt("items." + yamlKey + ".stats.fishing_speed", 0);

        return fishingSpeed; // will be -2, -1, 0, 1, 2, etc.
    }

    // --- Get multifish count from item ---
    public int getMultiFishFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;

        NamespacedKey ITEM_ID_KEY = new NamespacedKey(plugin, "item_id");
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
        if (id == null) return 1;

        String yamlKey = getYamlKeyFromId(id);
        if (yamlKey == null) return 1;

        FileConfiguration cfg = getCustomItemsConfig();
        return cfg.getInt("items." + yamlKey + ".stats.multifish", 1);
    }


    public void clearCustomItems() {
        // Clear all maps
        customItems.clear();
        customItemsById.clear();
        yamlKeyById.clear();

        // Reset configs to reload them fresh next time
        if (customItemsFile != null && customItemsFile.exists()) {
            customItemsConfig = YamlConfiguration.loadConfiguration(customItemsFile);
        }
        if (armorSetsFile != null && armorSetsFile.exists()) {
            armorSetsConfig = YamlConfiguration.loadConfiguration(armorSetsFile);
        }
        if (fishingSpeedsFile != null && fishingSpeedsFile.exists()) {
            fishingSpeedsConfig = YamlConfiguration.loadConfiguration(fishingSpeedsFile);
        }
    }

    @EventHandler
    public void onSpawnEggUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        // Check if it's any mob spawn egg
        if (item.getType().name().endsWith("_SPAWN_EGG")) {
            event.setCancelled(true); // block the egg
        }

        if (item.getType() == Material.ENDER_PEARL) {
            event.setCancelled(true); // block ender pearl use
        }
    
    }

    public static String getStaticRainbow(String text) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char c : text.toCharArray()) {
            sb.append(RAINBOW_COLORS[i % RAINBOW_COLORS.length]).append(c);
            i++;
        }
        return sb.toString();
    }


    public FileConfiguration getCustomItemsConfig() {
        return customItemsConfig;
    }

    public FileConfiguration getArmorSetsConfig() {
        return armorSetsConfig;
    }

    public FileConfiguration getFishingSpeedsConfig() {
        return fishingSpeedsConfig;
    }

    public Map<String, ItemStack> getCustomItems() {
        return customItems;
    }

    public Map<Integer, RarityInfo> getRarityMap() {
        return RARITY_MAP;
    }
}