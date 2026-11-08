package com.geotowny.listener;

import com.geotowny.web.ChatBuffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class WebChatListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ChatBuffer.getInstance().add(event.getPlayer().getName(), event.getMessage(), false);
    }
}