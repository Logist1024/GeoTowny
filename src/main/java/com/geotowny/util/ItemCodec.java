package com.geotowny.util;

import com.geotowny.Log;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(stack);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException | RuntimeException e) {
            Log.warn("物品序列化失败: {}", e.getMessage());
            return null;
        }
    }

    public static ItemStack decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            Object value = in.readObject();
            return value instanceof ItemStack stack ? stack : null;
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            Log.warn("物品反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    public static ItemStack single(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }
}