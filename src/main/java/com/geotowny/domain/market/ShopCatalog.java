package com.geotowny.domain.market;

import com.geotowny.domain.money.Money;

import java.util.ArrayList;
import java.util.List;

public final class ShopCatalog {

    public static final String CAT_VANILLA = "VANILLA";

    private ShopCatalog() {
    }

    public static List<MarketItem> defaults() {
        List<MarketItem> list = new ArrayList<>();
        add(list, "minecraft:wheat", "小麦", 8.0);
        add(list, "minecraft:wheat_seeds", "小麦种子", 2.0);
        add(list, "minecraft:carrot", "胡萝卜", 5.0);
        add(list, "minecraft:potato", "马铃薯", 5.0);
        add(list, "minecraft:beetroot", "甜菜根", 6.0);
        add(list, "minecraft:beetroot_seeds", "甜菜根种子", 2.0);
        add(list, "minecraft:pumpkin", "南瓜", 10.0);
        add(list, "minecraft:melon_slice", "西瓜片", 4.0);
        add(list, "minecraft:melon_seeds", "西瓜种子", 2.0);
        add(list, "minecraft:pumpkin_seeds", "南瓜种子", 2.0);
        add(list, "minecraft:sugar_cane", "甘蔗", 6.0);
        add(list, "minecraft:cocoa_beans", "可可豆", 7.0);
        add(list, "minecraft:nether_wart", "地狱疣", 12.0);
        add(list, "minecraft:sweet_berries", "甜浆果", 5.0);
        add(list, "minecraft:torchflower_seeds", "火把花种子", 15.0);
        add(list, "minecraft:pitcher_pod", "瓶子草荚果", 25.0);
        add(list, "minecraft:bread", "面包", 12.0);
        add(list, "minecraft:apple", "苹果", 8.0);
        add(list, "minecraft:cooked_beef", "牛排", 18.0);
        add(list, "minecraft:cooked_porkchop", "熟猪排", 16.0);
        add(list, "minecraft:cooked_cod", "熟鳕鱼", 12.0);
        add(list, "minecraft:egg", "鸡蛋", 5.0);
        add(list, "minecraft:sugar", "糖", 4.0);
        add(list, "minecraft:honey_bottle", "蜂蜜瓶", 20.0);
        add(list, "minecraft:cake", "蛋糕", 40.0);
        add(list, "minecraft:coal", "煤炭", 6.0);
        add(list, "minecraft:iron_ingot", "铁锭", 30.0);
        add(list, "minecraft:gold_ingot", "金锭", 60.0);
        add(list, "minecraft:diamond", "钻石", 300.0);
        add(list, "minecraft:netherite_ingot", "下界合金锭", 1500.0);
        add(list, "minecraft:emerald", "绿宝石", 200.0);
        add(list, "minecraft:lapis_lazuli", "青金石", 25.0);
        add(list, "minecraft:redstone", "红石粉", 15.0);
        add(list, "minecraft:quartz", "下界石英", 18.0);
        add(list, "minecraft:amethyst_shard", "紫水晶碎片", 22.0);
        add(list, "minecraft:copper_ingot", "铜锭", 20.0);
        add(list, "minecraft:oak_log", "橡木原木", 8.0);
        add(list, "minecraft:spruce_log", "云杉原木", 8.0);
        add(list, "minecraft:birch_log", "白桦原木", 8.0);
        add(list, "minecraft:oak_planks", "橡木木板", 4.0);
        add(list, "minecraft:stone", "石头", 3.0);
        add(list, "minecraft:cobblestone", "圆石", 2.0);
        add(list, "minecraft:oak_leaves", "橡树树叶", 1.0);
        add(list, "minecraft:deepslate", "深板岩", 4.0);
        add(list, "minecraft:cobbled_deepslate", "深板岩圆石", 3.0);
        add(list, "minecraft:deepslate_bricks", "深板岩砖", 8.0);
        add(list, "minecraft:deepslate_tiles", "深板岩瓦", 9.0);
        add(list, "minecraft:chiseled_deepslate", "錾制深板岩", 12.0);
        add(list, "minecraft:polished_deepslate", "磨制深板岩", 7.0);
        add(list, "minecraft:deepslate_coal_ore", "深层煤矿石", 9.0);
        add(list, "minecraft:deepslate_iron_ore", "深层铁矿石", 35.0);
        add(list, "minecraft:deepslate_copper_ore", "深层铜矿石", 24.0);
        add(list, "minecraft:deepslate_gold_ore", "深层金矿石", 65.0);
        add(list, "minecraft:deepslate_redstone_ore", "深层红石矿石", 18.0);
        add(list, "minecraft:deepslate_lapis_ore", "深层青金石矿石", 28.0);
        add(list, "minecraft:deepslate_diamond_ore", "深层钻石矿石", 320.0);
        add(list, "minecraft:deepslate_emerald_ore", "深层绿宝石矿石", 220.0);
        add(list, "minecraft:raw_iron", "粗铁", 30.0);
        add(list, "minecraft:raw_copper", "粗铜", 20.0);
        add(list, "minecraft:raw_gold", "粗金", 60.0);
        add(list, "minecraft:glass", "玻璃", 5.0);
        add(list, "minecraft:brick", "红砖", 6.0);
        add(list, "minecraft:torch", "火把", 3.0);
        add(list, "minecraft:glowstone", "荧石", 25.0);
        add(list, "minecraft:obsidian", "黑曜石", 50.0);
        add(list, "minecraft:netherrack", "下界岩", 2.0);
        add(list, "minecraft:end_stone", "末地石", 8.0);
        add(list, "minecraft:redstone_block", "红石块", 135.0);
        add(list, "minecraft:iron_block", "铁块", 270.0);
        add(list, "minecraft:gold_block", "金块", 540.0);
        add(list, "minecraft:diamond_block", "钻石块", 2700.0);
        add(list, "minecraft:stick", "木棍", 1.0);
        add(list, "minecraft:string", "线", 10.0);
        add(list, "minecraft:leather", "皮革", 15.0);
        add(list, "minecraft:bone", "骨头", 8.0);
        add(list, "minecraft:ender_pearl", "末影珍珠", 80.0);
        add(list, "minecraft:blaze_rod", "烈焰棒", 60.0);
        add(list, "minecraft:gunpowder", "火药", 20.0);
        add(list, "minecraft:paper", "纸", 3.0);
        add(list, "minecraft:book", "书", 12.0);
        add(list, "minecraft:oak_sapling", "橡树树苗", 12.0);
        add(list, "minecraft:spruce_sapling", "云杉树苗", 12.0);
        add(list, "minecraft:birch_sapling", "白桦树苗", 12.0);
        add(list, "minecraft:jungle_sapling", "丛林树苗", 15.0);
        add(list, "minecraft:acacia_sapling", "金合欢树苗", 12.0);
        add(list, "minecraft:dark_oak_sapling", "深色橡树树苗", 15.0);
        add(list, "minecraft:mangrove_propagule", "红树胎生苗", 18.0);
        add(list, "minecraft:cherry_sapling", "樱花树苗", 20.0);
        add(list, "minecraft:azalea", "杜鹃花丛", 16.0);
        add(list, "minecraft:flowering_azalea", "盛开的杜鹃花丛", 20.0);
        add(list, "minecraft:mangrove_leaves", "红树树叶", 2.0);
        add(list, "minecraft:nether_star", "下界之星", 5000.0);
        return list;
    }

    private static void add(List<MarketItem> list, String itemId, String displayName, double basePrice) {
        list.add(new MarketItem(itemId, displayName, CAT_VANILLA, Money.of(basePrice)));
    }
}