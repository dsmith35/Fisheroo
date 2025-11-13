package com.dylan.fisheroo.boss;

import org.bukkit.entity.EntityType;
import java.util.List;
import java.util.Map;

public class Boss {
    private final String id;
    private final String displayName;
    private final EntityType type;
    private final List<BossLootEntry> drops;
    private final double hp;
    private final Map<String, String> gear;
    private final double size;
    private final Map<String, Object> nbtData;
    private final int rarity;

    public Boss(String id, String displayName, EntityType type, List<BossLootEntry> drops, double hp, Map<String, String> gear, double size, int rarity, Map<String, Object> nbtData) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.drops = drops;
        this.hp = hp;
        this.gear = gear;
        this.size = size;
        this.rarity = rarity;
        this.nbtData = nbtData;
        
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public EntityType getType() { return type; }
    public double getHp() {return hp; }
    public List<BossLootEntry> getDrops() { return drops; }
    public Map<String, String> getGear() { return gear; }
    public Double getSize() { return size; }
    public Map<String, Object> getNbtData() { return nbtData; }
    public int getRarity() { return rarity; }
}
