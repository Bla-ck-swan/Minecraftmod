package com.yourname.elytraslot;

import net.minecraft.world.item.ItemStack;

/**
 * 注入到 HumanoidRenderState，在渲染状态里单独存放鞘翅，
 * 与 chestEquipment（真实胸甲）分离，供 WingsLayer 使用。
 */
public interface ElytraRenderStateAccess {
    ItemStack elytraslot$getElytra();
    void elytraslot$setElytra(ItemStack stack);
}
