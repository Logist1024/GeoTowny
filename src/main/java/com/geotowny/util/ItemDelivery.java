package com.geotowny.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ItemDelivery {

    private ItemDelivery() {
    }

    public static void give(Player player, ItemStack template, int quantity) {
        if (player == null || template == null || template.getType() == Material.AIR || quantity <= 0) {
            return;
        }
        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = quantity;
        while (remaining > 0) {
            int amount = Math.min(maxStack, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(amount);
            give(player, stack);
            remaining -= amount;
        }
    }

    public static void give(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}