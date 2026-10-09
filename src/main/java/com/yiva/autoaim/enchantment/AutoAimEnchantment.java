package com.yiva.autoaim.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem; // 我们让它适用于任何手持物品，所以用EnchantmentCategory.WEAPON或创建自定义类别
// 建议：让它适用于所有可持有的物品（包括弓、剑、工具等），这样更方便。
// 你可以使用 EnchantmentCategory.WEAPON 或 EnchantmentCategory.BREAKABLE，甚至自定义。
// 这里我们使用 EnchantmentCategory.WEAPON，因为它允许在剑、斧、三叉戟等上附魔。
// 如果你想让它只适用于弓，可以改为 EnchantmentCategory.BOW。

public class AutoAimEnchantment extends Enchantment {
    public AutoAimEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1; // 只有一级
    }

    @Override
    public int getMinCost(int level) {
        return 10;
    }

    @Override
    public int getMaxCost(int level) {
        return 30;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        // 允许在大部分手持物品上附魔（包括弓、剑、镐等）
        return true;
    }

    @Override
    public boolean isTreasureOnly() {
        return false; // 可以在附魔台获得
    }

    @Override
    public boolean isTradeable() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }
}