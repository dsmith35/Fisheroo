package com.dylan.fisheroo.boss;

public class BossLootEntry {
    private final String itemKey;
    private final int min;
    private final int max;
    private final double chance;

    public BossLootEntry(String itemKey, int min, int max, double chance) {
        this.itemKey = itemKey;
        this.min = min;
        this.max = max;
        this.chance = chance;
    }

    public String getItemKey() { return itemKey; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    public double getChance() { return chance; }

    public int getRandomAmount() {
        return min + (int)(Math.random() * (max - min + 1));
    }
}
