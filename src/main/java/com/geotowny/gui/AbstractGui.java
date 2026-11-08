package com.geotowny.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public abstract class AbstractGui implements InventoryHolder {

    protected final Plugin plugin;
    protected final Player player;
    private final Inventory inventory;

    protected AbstractGui(Plugin plugin, Player player, int size, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public abstract void handleClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) {
    }
}