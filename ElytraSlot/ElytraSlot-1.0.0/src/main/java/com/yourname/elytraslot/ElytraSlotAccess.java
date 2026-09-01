package com.yourname.elytraslot;

import net.minecraft.world.item.ItemStack;

/**
 * 注入到 Player，提供鞘翅槽位数据访问。
 */
public interface ElytraSlotAccess {
    ItemStack elytraslot$getStack();
    void elytraslot$setStack(ItemStack stack);

    /** 真实胸甲槽物品（绕过 getItemBySlot 劫持），用于渲染胸甲 */
    ItemStack elytraslot$getRealChest();

    /** 强制同步鞘翅槽到所有视角（用于耐久变化这类"对象内部状态改变但引用没变"的场景） */
    void elytraslot$forceSync();
}
