package com.geotowny.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemCodecTest {

    @Test
    void rejectsNullAndAir() {
        assertThat(ItemCodec.encode(null)).isNull();
        assertThat(ItemCodec.encode(new ItemStack(Material.AIR))).isNull();
    }

    @Test
    void invalidDataReturnsNull() {
        assertThat(ItemCodec.decode(null)).isNull();
        assertThat(ItemCodec.decode("")).isNull();
        assertThat(ItemCodec.decode("not-base64")).isNull();
    }

    @Test
    void singleCopiesWithAmountOne() {
        ItemStack stack = new ItemStack(Material.STONE, 32);
        ItemStack single = ItemCodec.single(stack);
        assertThat(single.getAmount()).isEqualTo(1);
        assertThat(stack.getAmount()).isEqualTo(32);
        assertThat(ItemCodec.single(null)).isNull();
    }
}