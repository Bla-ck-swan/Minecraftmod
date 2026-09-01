package com.yourname.elytraslot.mixin.client;

import com.yourname.elytraslot.ElytraRenderStateAccess;
import com.yourname.elytraslot.ElytraSlotAccess;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复：胸甲 + 鞘翅同时穿戴时，模型里胸甲不显示 / 鞘翅显示异常。
 * extractHumanoidRenderState 里 chestEquipment = getItemBySlot(CHEST)，
 * 被劫持成鞘翅。这里在 TAIL 把 chestEquipment 还原为真实胸甲，
 * 并把鞘翅单独存到渲染状态的 elytra 字段，供 WingsLayer 渲染。
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {

    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void elytraslot$splitChestAndElytra(LivingEntity entity, HumanoidRenderState state, float partialTick, ItemModelResolver resolver, CallbackInfo ci) {
        if (!(entity instanceof Player player)) return;

        ItemStack elytra = ((ElytraSlotAccess) player).elytraslot$getStack();
        if (elytra.isEmpty() || elytra.getItem() != Items.ELYTRA) return;

        ((ElytraRenderStateAccess) state).elytraslot$setElytra(elytra);
        state.chestEquipment = ((ElytraSlotAccess) player).elytraslot$getRealChest();
    }
}
