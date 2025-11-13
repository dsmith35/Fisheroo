package com.dylan.fisheroo.util;

import ca.tweetzy.skulls.Skulls;
import ca.tweetzy.skulls.api.interfaces.Skull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SkullUtil {

    public static ItemStack getSkullById(int id) {
        Skull skull = Skulls.getSkullManager().getSkull(id);
        return skull != null ? skull.getItemStack() : null;
    }

    public static void giveSkull(Player player, int id) {
        ItemStack skull = getSkullById(id);
        if (skull != null) {
            player.getInventory().addItem(skull);
        } else {
            player.sendMessage("§cThat skull ID doesn't exist!");
        }
    }
}
