package com.dylan.fisheroo.fishing;

public class LootEntry {
    public String itemId;
    public int rarity;
    public double baseChance;
    public double finalChance;

    public LootEntry(String itemId, int rarity, double baseChance) {
        this.itemId = itemId;
        this.rarity = rarity;
        this.baseChance = baseChance;
        this.finalChance = 0;
    }
}